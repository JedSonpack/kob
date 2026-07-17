<template>
  <ContentField>
    <div class="agent-lab">
      <div class="d-flex justify-content-between align-items-center mb-3">
        <h3 class="mb-0">Agent Lab</h3>
        <span v-if="currentTask" class="agent-task-status">
          <span class="badge" :class="statusBadge(currentTask.status)">
            {{ statusText(currentTask.status) }}
          </span>
          <span v-if="!isTerminal(currentTask.status)" class="text-muted ms-2">
            轮次 {{ currentTask.currentIteration }}/{{ currentTask.maxIterations }}
          </span>
        </span>
      </div>

      <div v-if="errorMessage" class="alert alert-danger py-2">{{ errorMessage }}</div>

      <!-- 任务创建 / 历史选择区 -->
      <div class="agent-section">
        <AgentTaskForm
          v-if="!currentTask || isTerminal(currentTask.status)"
          :creating="creating"
          @submit="onCreate"
        />
        <div v-else class="d-flex align-items-center gap-2 flex-wrap">
          <button
            class="btn btn-outline-danger btn-sm"
            :disabled="cancelling"
            @click="onCancel"
          >
            {{ cancelling ? "取消中..." : "取消任务" }}
          </button>
          <span class="text-muted">任务运行中，每秒刷新进度...</span>
        </div>

        <div class="mt-3" v-if="tasks.length">
          <h6 class="text-muted">历史任务</h6>
          <select
            class="form-select form-select-sm"
            style="width: auto"
            @change="onSelectTask($event)"
          >
            <option value="">选择历史任务...</option>
            <option v-for="t in tasks" :key="t.id" :value="t.id">
              #{{ t.id }} [{{ t.status }}] {{ truncate(t.goal, 24) }}
            </option>
          </select>
        </div>
      </div>

      <!-- Workflow 时间线 -->
      <div class="agent-section" v-if="currentTask">
        <h6 class="text-muted">Workflow 时间线</h6>
        <AgentWorkflowTimeline
          :status="currentTask.status"
          :steps="currentTask.steps || []"
        />
      </div>

      <!-- 版本对比 -->
      <div class="agent-section" v-if="currentTask">
        <h6 class="text-muted">版本对比</h6>
        <AgentVersionTable
          :versions="displayedVersions"
          :bestVersionId="currentTask.bestVersionId"
          :canSave="canSave"
          :savedVersionId="savedVersionId"
          @view="onViewVersion"
          @save="onSaveVersion"
        />
      </div>

      <!-- 隐藏验证集指标（仅终态） -->
      <div
        class="agent-section"
        v-if="currentTask && canShowHiddenMetrics(currentTask) && currentTask.hiddenEvaluation"
      >
        <h6 class="text-muted">隐藏验证集指标</h6>
        <div class="agent-metrics">
          <span>局数：{{ currentTask.hiddenEvaluation.gameCount }}</span>
          <span>综合得分：{{ formatMetric(currentTask.hiddenEvaluation.score, 3) }}</span>
          <span>胜率：{{ formatMetric(pct(currentTask.hiddenEvaluation.winRate), 1) }}%</span>
          <span>P95：{{ formatMetric(currentTask.hiddenEvaluation.decisionP95Ms, 0) }} ms</span>
          <span>非法移动：{{ currentTask.hiddenEvaluation.invalidMoveCount }}</span>
        </div>
      </div>

      <!-- Trace -->
      <div class="agent-section" v-if="currentTask">
        <h6 class="text-muted">Trace</h6>
        <AgentTraceTable :steps="currentTask.steps || []" />
      </div>

      <!-- 版本源码与差异 -->
      <div class="agent-section" v-if="currentVersion">
        <div class="d-flex justify-content-between align-items-center">
          <h6 class="text-muted mb-0">版本源码 - V{{ currentVersion.iteration }}</h6>
          <button class="btn btn-link btn-sm p-0" @click="onCloseVersion">收起</button>
        </div>
        <div v-if="currentVersion.changeReason" class="text-muted small mt-1">
          修改原因：{{ currentVersion.changeReason }}
        </div>
        <div v-if="diff" class="agent-diff-summary mt-1">
          <span class="text-success">+{{ diff.added }}</span>
          <span class="text-danger ms-2">-{{ diff.removed }}</span>
          <pre v-if="diff.lines.length" class="agent-diff mt-2"><code
            v-for="(line, i) in diff.lines"
            :key="i"
            :class="diffLineClass(line)"
            >{{ line }}</code></pre>
        </div>
        <div v-else class="text-muted small mt-1">初始生成版本，无父版本可对比</div>
        <pre class="agent-source mt-2"><code>{{ currentVersion.sourceCode || "（无源码）" }}</code></pre>
      </div>

      <!-- 代表性录像（仅终态） -->
      <div class="agent-section" v-if="currentTask && canShowHiddenMetrics(currentTask)">
        <h6 class="text-muted">代表性录像</h6>
        <div v-if="!replays.length" class="text-muted small">暂无录像</div>
        <div v-else>
          <div class="d-flex gap-2 mb-2 flex-wrap">
            <button
              v-for="r in replays"
              :key="r.id"
              class="btn btn-sm btn-outline-secondary"
              @click="selectedRun = r"
            >
              {{ resultLabel(r.result) }} vs {{ opponentLabel(r.opponentKey) }}
            </button>
          </div>
          <AgentReplayPanel v-if="selectedRun" :run="selectedRun" />
        </div>
      </div>

      <!-- 终态操作 -->
      <div class="agent-section" v-if="currentTask && isTerminal(currentTask.status)">
        <button class="btn btn-outline-primary btn-sm" @click="onNewTask">新建任务</button>
      </div>
    </div>
  </ContentField>
</template>

<script>
import { computed, onMounted, onBeforeUnmount, ref } from "vue";
import { useStore } from "vuex";
import ContentField from "@/components/ContentField.vue";
import AgentTaskForm from "@/components/agent/AgentTaskForm.vue";
import AgentWorkflowTimeline from "@/components/agent/AgentWorkflowTimeline.vue";
import AgentVersionTable from "@/components/agent/AgentVersionTable.vue";
import AgentTraceTable from "@/components/agent/AgentTraceTable.vue";
import AgentReplayPanel from "@/components/agent/AgentReplayPanel.vue";
import {
  buildLineDiff,
  canShowHiddenMetrics,
  displayVersions,
  formatMetric,
  isTerminal,
  representativeReplays,
} from "@/assets/scripts/agentViewModel";

const STATUS_TEXT = {
  CREATED: "已创建",
  GENERATING: "生成代码",
  COMPILING: "沙箱编译",
  REPAIRING: "修复编译",
  EVALUATING: "公开评测",
  ANALYZING: "失败分析",
  IMPROVING: "策略改进",
  VALIDATING: "隐藏验证",
  COMPLETED: "已完成",
  FAILED: "已失败",
  CANCELLED: "已取消",
};

const OPPONENT_LABELS = { greedy: "贪婪", territory: "圈地", safe: "保守" };

export default {
  components: {
    ContentField,
    AgentTaskForm,
    AgentWorkflowTimeline,
    AgentVersionTable,
    AgentTraceTable,
    AgentReplayPanel,
  },
  setup() {
    const store = useStore();
    let cancelling = ref(false);
    let parentVersion = ref(null);
    let savedVersionId = ref(null);
    let selectedRun = ref(null);

    const currentTask = computed(() => store.state.agent.currentTask);
    const tasks = computed(() => store.state.agent.tasks);
    const creating = computed(() => store.state.agent.creating);
    const errorMessage = computed(() => store.state.agent.errorMessage);
    const currentVersion = computed(() => store.state.agent.currentVersion);
    const displayedVersions = computed(() => displayVersions(currentTask.value));

    const canSave = computed(
      () => !!(currentTask.value && currentTask.value.status === "COMPLETED")
    );
    const replays = computed(() =>
      representativeReplays(currentTask.value && currentTask.value.representativeRuns)
    );
    const diff = computed(() =>
      parentVersion.value && currentVersion.value
        ? buildLineDiff(parentVersion.value.sourceCode, currentVersion.value.sourceCode)
        : null
    );

    const statusText = (s) => STATUS_TEXT[s] || s;
    const statusBadge = (s) =>
      s === "COMPLETED" ? "bg-success" : isTerminal(s) ? "bg-danger" : "bg-primary";
    const truncate = (s, n) => (s && s.length > n ? s.slice(0, n) + "..." : s || "");
    const pct = (v) => (v == null ? null : v * 100);
    const opponentLabel = (k) => OPPONENT_LABELS[k] || k || "-";
    const resultLabel = (r) => ({ WIN: "胜", LOSS: "负", DRAW: "平" }[r] || r || "-");
    const diffLineClass = (line) =>
      line.startsWith("+") ? "diff-add" : line.startsWith("-") ? "diff-del" : "";

    const onCreate = async (payload) => {
      const id = await store.dispatch("agent/createTask", payload);
      if (id != null) {
        savedVersionId.value = null;
        selectedRun.value = null;
        parentVersion.value = null;
        await store.dispatch("agent/openTask", id);
        await store.dispatch("agent/loadTasks");
      }
    };

    const onSelectTask = async (event) => {
      const id = event.target.value;
      if (!id) return;
      savedVersionId.value = null;
      selectedRun.value = null;
      parentVersion.value = null;
      await store.dispatch("agent/openTask", Number(id));
      event.target.value = "";
    };

    const onViewVersion = async (version) => {
      const current = await store.dispatch("agent/loadVersion", version.id);
      if (current && current.parentVersionId) {
        // 加载父版本用于差异对比；loadVersion 会覆盖 currentVersion，需恢复
        parentVersion.value = await store.dispatch(
          "agent/loadVersion",
          current.parentVersionId
        );
        store.commit("agent/setCurrentVersion", current);
      } else {
        parentVersion.value = null;
      }
    };

    const onCloseVersion = () => {
      store.commit("agent/setCurrentVersion", null);
      parentVersion.value = null;
    };

    const onSaveVersion = async (version) => {
      const resp = await store.dispatch("agent/saveVersion", {
        versionId: version.id,
        payload: { title: "Agent V" + version.iteration },
      });
      if (resp && resp.error_message === "success") {
        savedVersionId.value = version.id;
      }
    };

    const onCancel = async () => {
      if (!currentTask.value) return;
      cancelling.value = true;
      await store.dispatch("agent/cancelTask", currentTask.value.id);
      cancelling.value = false;
      await store.dispatch("agent/loadTasks");
    };

    const onNewTask = () => {
      store.dispatch("agent/stopPolling");
      store.commit("agent/reset");
      savedVersionId.value = null;
      selectedRun.value = null;
      parentVersion.value = null;
    };

    onMounted(async () => {
      await store.dispatch("agent/loadTasks");
      const running = store.state.agent.tasks.find((t) => !isTerminal(t.status));
      if (running) {
        await store.dispatch("agent/openTask", running.id);
      }
    });

    onBeforeUnmount(() => {
      store.dispatch("agent/stopPolling");
    });

    return {
      currentTask,
      tasks,
      creating,
      errorMessage,
      currentVersion,
      displayedVersions,
      canSave,
      replays,
      diff,
      cancelling,
      savedVersionId,
      selectedRun,
      isTerminal,
      canShowHiddenMetrics,
      formatMetric,
      statusText,
      statusBadge,
      truncate,
      pct,
      opponentLabel,
      resultLabel,
      diffLineClass,
      onCreate,
      onSelectTask,
      onViewVersion,
      onCloseVersion,
      onSaveVersion,
      onCancel,
      onNewTask,
    };
  },
};
</script>

<style scoped>
.agent-lab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.agent-section {
  border-top: 1px solid #f0f0f0;
  padding-top: 12px;
}
.agent-metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
}
.agent-source {
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  padding: 12px;
  font-size: 12px;
  max-height: 360px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.agent-diff {
  background: #f6f8fa;
  border: 1px solid #e1e4e8;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 12px;
  max-height: 240px;
  overflow: auto;
}
.agent-diff code {
  display: block;
  white-space: pre;
}
.diff-add {
  color: #1a7f37;
  background: #dafbe1;
}
.diff-del {
  color: #cf222e;
  background: #ffebe9;
}
</style>

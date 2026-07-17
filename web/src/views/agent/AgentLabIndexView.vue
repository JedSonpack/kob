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
        <div
          v-else
          class="d-flex align-items-center gap-2 flex-wrap"
        >
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
          @view="onViewVersion"
        />
      </div>

      <!-- Trace -->
      <div class="agent-section" v-if="currentTask">
        <h6 class="text-muted">Trace</h6>
        <AgentTraceTable :steps="currentTask.steps || []" />
      </div>

      <!-- 版本源码 -->
      <div class="agent-section" v-if="currentVersion">
        <div class="d-flex justify-content-between align-items-center">
          <h6 class="text-muted mb-0">
            版本源码 - V{{ currentVersion.iteration }}
          </h6>
          <button class="btn btn-link btn-sm p-0" @click="onCloseVersion">收起</button>
        </div>
        <div v-if="currentVersion.changeReason" class="text-muted small mt-1">
          修改原因：{{ currentVersion.changeReason }}
        </div>
        <pre class="agent-source mt-2"><code>{{ currentVersion.sourceCode || "（无源码）" }}</code></pre>
      </div>

      <!-- 终态操作 -->
      <div class="agent-section" v-if="currentTask && isTerminal(currentTask.status)">
        <button class="btn btn-outline-primary btn-sm" @click="onNewTask">
          新建任务
        </button>
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
import { displayVersions, isTerminal } from "@/assets/scripts/agentViewModel";

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

export default {
  components: {
    ContentField,
    AgentTaskForm,
    AgentWorkflowTimeline,
    AgentVersionTable,
    AgentTraceTable,
  },
  setup() {
    const store = useStore();
    let cancelling = ref(false);

    const currentTask = computed(() => store.state.agent.currentTask);
    const tasks = computed(() => store.state.agent.tasks);
    const creating = computed(() => store.state.agent.creating);
    const errorMessage = computed(() => store.state.agent.errorMessage);
    const currentVersion = computed(() => store.state.agent.currentVersion);
    const displayedVersions = computed(() => displayVersions(currentTask.value));

    const statusText = (s) => STATUS_TEXT[s] || s;
    const statusBadge = (s) =>
      s === "COMPLETED"
        ? "bg-success"
        : isTerminal(s)
        ? "bg-danger"
        : "bg-primary";
    const truncate = (s, n) => (s && s.length > n ? s.slice(0, n) + "..." : s || "");

    const onCreate = async (payload) => {
      const id = await store.dispatch("agent/createTask", payload);
      if (id != null) {
        await store.dispatch("agent/openTask", id);
        await store.dispatch("agent/loadTasks");
      }
    };

    const onSelectTask = async (event) => {
      const id = event.target.value;
      if (!id) return;
      await store.dispatch("agent/openTask", Number(id));
      event.target.value = "";
    };

    const onViewVersion = async (version) => {
      await store.dispatch("agent/loadVersion", version.id);
    };

    const onCloseVersion = () => {
      store.commit("agent/setCurrentVersion", null);
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
    };

    onMounted(async () => {
      await store.dispatch("agent/loadTasks");
      // 自动恢复运行中的任务
      const running = store.state.agent.tasks.find(
        (t) => !isTerminal(t.status)
      );
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
      cancelling,
      isTerminal,
      statusText,
      statusBadge,
      truncate,
      onCreate,
      onSelectTask,
      onViewVersion,
      onCloseVersion,
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
</style>

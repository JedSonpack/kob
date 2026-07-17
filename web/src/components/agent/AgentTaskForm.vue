<template>
  <div class="agent-task-form">
    <div class="mb-3">
      <label class="form-label">策略目标</label>
      <textarea
        class="form-control"
        v-model="goal"
        :maxlength="1000"
        :disabled="creating"
        rows="3"
        placeholder="例如：尽量扩大可活动区域，避免进入狭窄通道"
      ></textarea>
      <div class="form-text">{{ goal.length }}/1000</div>
    </div>
    <div class="mb-3">
      <label class="form-label">最大迭代轮数</label>
      <select
        class="form-select"
        style="width: auto"
        v-model.number="maxIterations"
        :disabled="creating"
      >
        <option :value="1">1</option>
        <option :value="2">2</option>
        <option :value="3">3</option>
      </select>
    </div>
    <button
      class="btn btn-primary"
      :disabled="creating || !goal.trim()"
      @click="onSubmit"
    >
      {{ creating ? "进化中..." : "开始进化" }}
    </button>
  </div>
</template>

<script>
import { ref } from "vue";

export default {
  props: {
    creating: { type: Boolean, default: false },
  },
  emits: ["submit"],
  setup(props, { emit }) {
    let goal = ref("");
    let maxIterations = ref(3);
    const onSubmit = () => {
      if (props.creating || !goal.value.trim()) return;
      emit("submit", {
        goal: goal.value.trim(),
        maxIterations: maxIterations.value,
      });
    };
    return { goal, maxIterations, onSubmit };
  },
};
</script>

<style scoped>
.agent-task-form textarea {
  resize: vertical;
}
</style>

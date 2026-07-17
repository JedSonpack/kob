<template>
  <div class="agent-timeline">
    <div
      v-for="item in timeline"
      :key="item.phase"
      class="agent-timeline-slot"
      :class="'state-' + item.state"
    >
      <div class="agent-timeline-icon">{{ icon(item.state) }}</div>
      <div class="agent-timeline-label">{{ item.label }}</div>
    </div>
  </div>
</template>

<script>
import { computed } from "vue";
import { buildTimeline } from "@/assets/scripts/agentViewModel";

export default {
  props: {
    status: { type: String, default: "" },
    steps: { type: Array, default: () => [] },
  },
  setup(props) {
    const timeline = computed(() => buildTimeline(props.status, props.steps));
    const icon = (state) =>
      ({ done: "✓", running: "●", failed: "✕", pending: "○" }[state] || "○");
    return { timeline, icon };
  },
};
</script>

<style scoped>
.agent-timeline {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.agent-timeline-slot {
  min-width: 96px;
  min-height: 64px;
  padding: 8px 6px;
  border-radius: 6px;
  border: 1px solid #dee2e6;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f8f9fa;
}
.state-done {
  border-color: #198754;
  color: #198754;
}
.state-running {
  border-color: #0d6efd;
  color: #0d6efd;
  background: #e7f1ff;
}
.state-failed {
  border-color: #dc3545;
  color: #dc3545;
}
.state-pending {
  color: #6c757d;
}
.agent-timeline-icon {
  font-size: 18px;
}
.agent-timeline-label {
  font-size: 13px;
  margin-top: 4px;
}
</style>

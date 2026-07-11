<template>
  <div ref="parent" class="gamemap">
    <canvas ref="canvas" tabindex="0"></canvas>
    <!--画布标签,可以接受用户操作-->
  </div>
</template>

<script>
import { GameMap } from "@/assets/scripts/GameMap";
import { ref, onMounted, onUnmounted } from "vue"; //挂载结束后onMounted
import { useStore } from "vuex";

export default {
  setup() {
    let parent = ref(null);
    let canvas = ref(null);
    const store = useStore();
    onMounted(() => {
      store.commit(
        "updateGameObject",
        new GameMap(canvas.value.getContext("2d"), parent.value, store)
      );
    });

    onUnmounted(() => {  // 审计 3.1：卸载时销毁游戏对象，清理 rAF/监听/定时器
      const gameObject = store.state.pk.gameObject;
      if (gameObject) {
        gameObject.destroy();
      }
    });

    return {
      parent,
      canvas,
    };
  },
};
</script>

<style scoped>
div.gamemap {
  width: 100%; /*跟父元素等长 话游戏地图*/
  height: 100%;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
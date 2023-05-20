<template>
    <div ref="parent" class="gamemap"> 
        <canvas ref="canvas"></canvas> <!--画布标签-->
            </div>
</template>

<script>


import { GameMap } from "@/assets/scripts/GameMap"; 
import { ref, onMounted } from 'vue' //挂载结束后onMounted

export default {
    setup() {
        let parent = ref(null);
        let canvas = ref(null);

        onMounted(() => {
            new GameMap(canvas.value.getContext('2d'), parent.value)
        });

        return {
            parent,
            canvas
        }
    }
}
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
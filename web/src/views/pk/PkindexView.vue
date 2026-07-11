<template>
  <PlayGround v-if="$store.state.pk.status === 'playing'" />
  <MatchGround v-if="$store.state.pk.status === 'matching'" />
  <ResultBoard v-if="$store.state.pk.loser != 'none'" />
  <div
    class="user-color1"
    v-if="
      $store.state.pk.status === 'playing' &&
      parseInt($store.state.user.id) === parseInt($store.state.pk.a_id)
    "
  >
    您出生在左下角🐍🐍🐍
  </div>
  <div
    class="user-color2"
    v-if="
      $store.state.pk.status === 'playing' &&
      parseInt($store.state.user.id) === parseInt($store.state.pk.b_id)
    "
  >
    您出生在右上角🐍🐍🐍
  </div>
</template>

<script>
import PlayGround from "../../components/PlayGround.vue";
import { useStore } from "vuex";
import { onMounted, onUnmounted, watch } from "vue";
import MatchGround from "@/components/MatchGround.vue";
import ResultBoard from "@/components/ResultBoard.vue";
import { createGameEventDispatcher } from "@/assets/scripts/pkSocket";

export default {
  components: {
    PlayGround,
    MatchGround,
    ResultBoard,
  },
  setup() {
    const store = useStore();
    const wsProto = window.location.protocol === "https:" ? "wss" : "ws";
    const socketUrl = `${wsProto}://${window.location.host}/websocket/${store.state.user.token}/`;

    let socket = null;
    store.commit("updateIsRecord", false);
    const gameEventDispatcher = createGameEventDispatcher(
      () => store.state.pk.gameObject,
      (loser) => store.commit("updateLoser", loser)
    );
    store.commit("updateGameEventDispatcher", gameEventDispatcher);

    watch(
      () => store.state.pk.gameObject,
      () => gameEventDispatcher.flush()
    );

    onMounted(() => {
      store.commit("updateOpponent", {
        username: "我的对手",
        photo:
          "https://cdn.acwing.com/media/article/image/2022/08/09/1_1db2488f17-anonymous.png",
      });

      socket = new WebSocket(socketUrl);

      socket.onopen = () => {
        console.log("connected!");
        store.commit("updateSocket", socket);
      };

      socket.onmessage = (msg) => {
        const data = JSON.parse(msg.data);
        if (data.event === "start-matching") {
          // 匹配成功
          store.commit("updateOpponent", {
            username: data.opponent_username,
            photo: data.opponent_photo,
          });
          store.commit("updateGame", data.game);
          store.commit("updateStatus", "playing");  // 审计 3.3：去掉固定 100ms 延时，立即切对战
        } else if (data.event === "move") {
          gameEventDispatcher.dispatch(data);
        } else if (data.event === "result") {
          gameEventDispatcher.dispatch(data);
        }
      };

      socket.onclose = () => {
        console.log("disconnected!");
      };
    });

    onUnmounted(() => {
      store.commit("updateLoser", "none");
      store.commit("updateGameEventDispatcher", null);
      socket.close();
      store.commit("updateStatus", "matching");
    });
  },
};
</script>



<style scoped>
div.user-color1 {
  text-align: center;
  color: blue;
  font-size: 30px;
  font-style: italic;
  font-weight: 600;
}

div.user-color2 {
  text-align: center;
  color: red;
  font-style: italic;
  font-size: 30px;
  font-weight: 600;
}
</style>

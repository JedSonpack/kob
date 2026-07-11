<template>
  <PlayGround v-if="loaded" />
  <ContentField v-else>加载中...</ContentField>
</template>

<script>
import PlayGround from "../../components/PlayGround.vue";
import ContentField from "../../components/ContentField.vue";
import { ref, onMounted } from "vue";
import { useStore } from "vuex";
import { useRoute } from "vue-router";
import $ from "jquery";
import { populateRecordFromItem } from "../../assets/scripts/recordHelper";

export default {
  components: {
    PlayGround,
    ContentField,
  },
  setup() {
    const store = useStore();
    const route = useRoute();
    let loaded = ref(false);

    onMounted(() => {
      // 审计 3.2：若 Vuex 无录像数据（直达/刷新场景），按 URL 的 recordId 拉取
      if (store.state.record.is_record) {
        loaded.value = true;
        return;
      }
      const recordId = route.params.recordId;
      $.ajax({
        url: "https://app4186.acapp.acwing.com.cn/api/record/get/",
        data: { recordId },
        type: "get",
        headers: {
          Authorization: "Bearer " + store.state.user.token,
        },
        success(resp) {
          if (resp.error_message === "success") {
            populateRecordFromItem(store, resp.record_item);
            loaded.value = true;
          } else {
            console.log(resp);
          }
        },
        error(resp) {
          console.log(resp);
        },
      });
    });

    return { loaded };
  },
};
</script>

<style scoped></style>

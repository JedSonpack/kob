<template>
  <ContentField> 我的机器人 </ContentField>
</template>

<script>
import ContentField from "@/components/ContentField.vue";
import $ from "jquery";
import { useStore } from "vuex";

export default {
  components: { ContentField },
  component: {
    ContentField,
  },
  setup() {
    const content = useStore();

 $.ajax({
      url: "http://127.0.0.1:3000/user/bot/getlist/",
      type: "get",
      headers: {
        Authorization: "Bearer " + content.state.user.token,
      },
      success(resp) {
        console.log(resp);
      },
      error(resp) {
        console.log(resp);
      },
    });
  },
//     $.ajax({
//       url: "http://127.0.0.1:3000/user/bot/add/",
//       type: "post",
//       data: {
//         title: "bot标题",
//         description: "bot描述",
//         content: "bot代码",
//       },
//       headers: {
//         Authorization: "Bearer " + content.state.user.token,
//       },
//       success(resp) {
//         console.log(resp);
//       },
//       error(resp) {
//         console.log(resp);
//       },
//     });
//   },
//   $.ajax({
//       url: "http://127.0.0.1:3000/user/bot/remove/",
//       type: "post",
//       data: {
//         bot_id:100,
//       },
//       headers: {
//         Authorization: "Bearer " + content.state.user.token,
//       },
//       success(resp) {
//         console.log(resp);
//       },
//       error(resp) {
//         console.log(resp);
//       },
//     });
//   },
//  $.ajax({
//       url: "http://127.0.0.1:3000/user/bot/update/",
//       type: "post",
//       data: {
//         bot_id:54,
//         title: "bot标题2222",
//         description: "bot描述222",
//         content: "bot代码222",
//       },
//       headers: {
//         Authorization: "Bearer " + content.state.user.token,
//       },
//       success(resp) {
//         console.log(resp);
//       },
//       error(resp) {
//         console.log(resp);
//       },
//     });
//   },
};
</script>

<style scoped>
</style>
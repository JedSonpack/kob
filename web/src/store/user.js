import $ from "jquery";

export default {
  state: {
    id: "",
    username: "",
    photo: "",
    token: "",
    is_login: false,
  },
  getters: {
    //一般用不到
  },
  mutations: {
    //用来修改数据
    updateUser(state, user) {
      state.id = user.id;
      state.username = user.username;
      state.photo = user.photo;
      state.is_login = user.is_login;
    },

    updateToken(state, token) {
      state.token = token;
    },
    logout(state) {
      state.id = "";
      state.username = "";
      state.photo = "";
      state.is_login = false;
      state.token = "";
    },
  },

  actions: {
    //修改state
    login(context, data) {
      $.ajax({
        url: "http://127.0.0.1:3000/user/account/token/",
        type: "post",
        data: {
          username: data.username,
          password: data.password,
        },
        success(resp) {
          if (resp.error_message === "success") {
            context.commit("updateToken", resp.token);
            data.success(resp);
          } else {
            data.error(resp);
          }
        },
        error(resp) {
          data.error(resp);
        },
      });
    },
    getinfo(context, data) {
      $.ajax({
        url: "http://127.0.0.1:3000/user/account/info/",
        type: "get",
        headers: {
          Authorization: "Bearer " + context.state.token,
        },
        success(resp) {
          if (resp.error_message === "success") {
            context.commit("updateUser", {
              ...resp, //resp内容解析出来
              is_login: true, //增加一个属性
            });
            data.success(resp);
          } else {
            data.error(resp);
          }
        },
        error(resp) {
          data.error(resp);
        },
      });
    },
    logout(context) {
      context.commit("logout");
    },
  },
  modules: {},
};

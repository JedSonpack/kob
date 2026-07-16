<template>
  <div class="container">
    <div class="row">
      <div class="col-3" style="margin-top: 20px">
        <img :src="$store.state.user.photo" alt="" style="width: 100%" />
      </div>
      <div class="col-9">
        <div class="card" style="margin-top: 20px">
          <div class="card-header">
            <span style="font-size: 130%">我的Bot</span>
            <button
              type="button"
              class="btn btn-primary float-end"
              data-bs-toggle="modal"
              data-bs-target="#add-bot-btn"
            >
              创建Bot
            </button>

            <!-- Modal -->
            <div class="modal fade" id="add-bot-btn" tabindex="-1">
              <div class="modal-dialog modal-xl">
                <div class="modal-content">
                  <div class="modal-header">
                    <h1 class="modal-title fs-5" id="exampleModalLabel">
                      创建Bot
                    </h1>
                    <button
                      type="button"
                      class="btn-close"
                      data-bs-dismiss="modal"
                      aria-label="Close"
                    ></button>
                  </div>

                  <div class="modal-body">
                    <div class="mb-3">
                      <label for="add-bot-title" class="form-label"
                        >Bot名称</label
                      >
                      <input
                        type="text"
                        class="form-control"
                        id="add-bot-title"
                        placeholder="请输入Bot名称"
                        v-model="botadd.title"
                      />
                    </div>

                    <div class="mb-3">
                      <label for="add-bot-description" class="form-label"
                        >Bot简介</label
                      >
                      <textarea
                        class="form-control"
                        id="add-bot-description"
                        rows="3"
                        placeholder="请描述一下您牛逼的AI Bot"
                        v-model="botadd.description"
                      ></textarea>
                    </div>

                    <div class="mb-3">
                      <label for="add-bot-code" class="form-label"
                        >Bot代码</label
                      >
                      <VAceEditor
                        v-model:value="botadd.content"
                        @init="editorInit"
                        lang="c_cpp"
                        theme="textmate"
                        style="height: 300px"
                        :options="{
                          enableBasicAutocompletion: true, //启用基本自动完成
                          enableSnippets: true, // 启用代码段
                          enableLiveAutocompletion: true, // 启用实时自动完成
                          fontSize: 14, //设置字号
                          tabSize: 2, // 标签大小
                          showPrintMargin: false, //去除编辑器里的竖线
                          highlightActiveLine: true,
                        }"
                      />
                    </div>
                  </div>

                  <div class="modal-footer">
                    <div class="error-message">{{ botadd.error_message }}</div>
                    <button
                      type="button"
                      class="btn btn-secondary"
                      data-bs-dismiss="modal"
                    >
                      关闭
                    </button>
                    <button
                      type="button"
                      class="btn btn-primary"
                      @click="add_bot"
                    >
                      保存Bot
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="card-body">
            <table class="table table-striped table-hover">
              <thead>
                <tr>
                  <th>名称</th>
                  <th>创建时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="bot in bots" :key="bot.id">
                  <td>{{ bot.title }}</td>
                  <td>{{ bot.createtime }}</td>
                  <td>
                    <button
                      type="button"
                      class="btn btn-primary"
                      style="margin-left: -50px"
                      data-bs-toggle="modal"
                      :data-bs-target="'#update-bot-modal-' + bot.id"
                    >
                      修改
                    </button>

                    <button
                      type="button"
                      class="btn btn-danger"
                      style="margin-left: 30px"
                      @click="remove_bot(bot)"
                    >
                      删除
                    </button>
                    <div
                      class="modal fade"
                      :id="'update-bot-modal-' + bot.id"
                      tabindex="-1"
                    >
                      <div class="modal-dialog modal-xl">
                        <div class="modal-content">
                          <div class="modal-header">
                            <h5 class="modal-title">创建Bot</h5>
                            <button
                              type="button"
                              class="btn-close"
                              data-bs-dismiss="modal"
                              aria-label="Close"
                            ></button>
                          </div>

                          <div class="modal-body">
                            <div class="mb-3">
                              <label for="add-bot-title" class="form-label"
                                >名称</label
                              >
                              <input
                                v-model="bot.title"
                                type="text"
                                class="form-control"
                                id="add-bot-title"
                                placeholder="请输入Bot名称"
                              />
                            </div>
                            <div class="mb-3">
                              <label
                                for="add-bot-description"
                                class="form-label"
                                >简介</label
                              >
                              <textarea
                                v-model="bot.description"
                                class="form-control"
                                id="add-bot-description"
                                rows="3"
                                placeholder="请输入Bot简介"
                              ></textarea>
                            </div>
                            <div class="mb-3">
                              <label for="add-bot-code" class="form-label"
                                >Bot代码</label
                              >
                              <VAceEditor
                                v-model:value="bot.content"
                                @init="editorInit"
                                lang="c_cpp"
                                theme="textmate"
                                style="height: 300px"
                                :options="{
                                  enableBasicAutocompletion: true, //启用基本自动完成
                                  enableSnippets: true, // 启用代码段
                                  enableLiveAutocompletion: true, // 启用实时自动完成
                                  fontSize: 14, //设置字号
                                  tabSize: 2, // 标签大小
                                  showPrintMargin: false, //去除编辑器里的竖线
                                  highlightActiveLine: true,
                                }"
                              />
                            </div>
                          </div>

                          <div class="modal-footer">
                            <div class="error-message">
                              {{ bot.error_message }}
                            </div>
                            <button
                              type="button"
                              class="btn btn-secondary"
                              data-bs-dismiss="modal"
                            >
                              取消
                            </button>
                            <button
                              type="button"
                              class="btn btn-primary"
                              @click="update_bot(bot)"
                            >
                              保存修改
                            </button>
                          </div>
                        </div>
                      </div>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import $ from "jquery";
import { useStore } from "vuex";
import { ref, reactive } from "vue";
import { Modal } from "bootstrap/dist/js/bootstrap";
import { VAceEditor } from "vue3-ace-editor";
import ace from "ace-builds";
import "ace-builds/src-noconflict/mode-json";
import "ace-builds/src-noconflict/theme-chrome";
import "ace-builds/src-noconflict/ext-language_tools";
import "ace-builds/src-noconflict/mode-c_cpp";

/**
 * Bot 代码模板：输入解析已写好，用户只需改 nextMove 中「在这里写你的决策」一段。
 * 提交时仍是完整可编译类，后端执行器无需改动。
 */
const BOT_TEMPLATE = `package com.kob.test;
import java.io.File;
import java.util.*;
import java.util.function.Supplier;

/**
 * KOB Bot 模板——只需修改 nextMove 中「在这里写你的决策」下方的代码。
 *
 * input 格式（# 分隔）：地图#我sx#我sy#(我的历史操作)#对手sx#对手sy#(对手历史操作)
 *   地图：13×14 的 0/1 拼接，0=可走，1=墙；操作：0=上 1=右 2=下 3=左
 * 返回：0=上 1=右 2=下 3=左
 */
public class Bot implements java.util.function.Supplier<Integer> {
    static int[] dx = {-1, 0, 1, 0}, dy = {0, 1, 0, -1};

    public static Integer nextMove(String input) {
        String[] s = input.split("#");
        int[][] g = new int[13][14];
        for (int i = 0, k = 0; i < 13; i++)
            for (int j = 0; j < 14; j++, k++)
                if (s[0].charAt(k) == '1') g[i][j] = 1;

        // 自己的蛇身 aCells、对手蛇身 bCells、我的蛇头 head 已解析好
        List<int[]> aCells = getCells(Integer.parseInt(s[1]), Integer.parseInt(s[2]),
                s[3].substring(1, s[3].length() - 1));
        List<int[]> bCells = getCells(Integer.parseInt(s[4]), Integer.parseInt(s[5]),
                s[6].substring(1, s[6].length() - 1));
        for (int[] c : aCells) g[c[0]][c[1]] = 1;
        for (int[] c : bCells) g[c[0]][c[1]] = 1;
        int[] head = aCells.get(aCells.size() - 1);

        // ===== 在这里写你的决策（默认：找第一个不会撞的方向）=====
        for (int i = 0; i < 4; i++) {
            int x = head[0] + dx[i], y = head[1] + dy[i];
            if (x >= 0 && x < 13 && y >= 0 && y < 14 && g[x][y] == 0) return i;
        }
        // ===== 决策结束 =====

        return 0;
    }

    /** 以下为辅助方法，一般无需修改 **/

    static List<int[]> getCells(int sx, int sy, String steps) {
        List<int[]> res = new ArrayList<>();
        int x = sx, y = sy, step = 0;
        res.add(new int[]{x, y});
        for (int i = 0; i < steps.length(); i++) {
            int d = steps.charAt(i) - '0';
            x += dx[d]; y += dy[d];
            res.add(new int[]{x, y});
            if (!checkTail(++step)) res.remove(0);
        }
        return res;
    }

    static boolean checkTail(int step) { return step <= 10 || step % 3 == 1; }

    @Override
    public Integer get() {
        try { return nextMove(new Scanner(new File("input.txt")).next()); }
        catch (Exception e) { return 0; }
    }
}`;

export default {
  components: {
    VAceEditor,
  },

  setup() {
    const store = useStore();
    let bots = ref([]);

    ace.config.set(
      "basePath",
      "https://cdn.jsdelivr.net/npm/ace-builds@" +
        require("ace-builds").version +
        "/src-noconflict/"
    );

    const botadd = reactive({
      title: "",
      description: "",
      content: BOT_TEMPLATE,
      error_message: "",
    });

    const refresh_bots = () => {
      $.ajax({
        url: "/api/user/bot/getlist/",
        type: "get",
        headers: {
          Authorization: "Bearer " + store.state.user.token,
        },
        success(resp) {
          bots.value = resp;
        },
        error(resp) {
          console.log(resp);
        },
      });
    };

    refresh_bots();

    const add_bot = () => {
      botadd.error_message = "";
      $.ajax({
        url: "/api/user/bot/add/",
        type: "post",
        data: {
          title: botadd.title,
          description: botadd.description,
          content: botadd.content,
        },
        headers: {
          Authorization: "Bearer " + store.state.user.token, //验证信息
        },
        success(resp) {
          if (resp.error_message === "success") {
            botadd.title = "";
            botadd.description = "";
            botadd.content = BOT_TEMPLATE;
            //将模态框关闭
            Modal.getInstance("#add-bot-btn").hide();
            refresh_bots();
          } else {
            botadd.error_message = resp.error_message;
          }
        },
      });
    };
    const remove_bot = (bot) => {
      $.ajax({
        url: "/api/user/bot/remove/",
        type: "post",
        data: {
          bot_id: bot.id,
        },
        headers: {
          Authorization: "Bearer " + store.state.user.token,
        },
        success(resp) {
          if (resp.error_message === "success") {
            refresh_bots();
          }
        },
      });
    };

    const update_bot = (bot) => {
      botadd.error_message = "";
      $.ajax({
        url: "/api/user/bot/update/",
        type: "post",
        data: {
          bot_id: bot.id,
          title: bot.title,
          description: bot.description,
          content: bot.content,
        },
        headers: {
          Authorization: "Bearer " + store.state.user.token,
        },
        success(resp) {
          if (resp.error_message === "success") {
            Modal.getInstance("#update-bot-modal-" + bot.id).hide();
            refresh_bots();
          } else {
            botadd.error_message = resp.error_message;
          }
        },
      });
    };

    return {
      bots,
      botadd,
      add_bot,
      remove_bot,
      update_bot,
    };
  },
};
</script>

<style scoped>
div.error-message {
  color: red;
}
</style>
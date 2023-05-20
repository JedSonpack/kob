import { AcGameObject } from "./AcGameObject"; //非default加括号，default不加括号
import { Wall } from "./Wall"

export class GameMap extends AcGameObject {  //继承
    constructor(ctx, parent) { //画布，画布的父元素，动态修改长宽
        super(); //基类构造函数

        this.ctx = ctx;
        this.parent = parent;
        this.L = 0; //一个单位的绝对长度

        this.rows = 13;
        this.cols = 13;
        
        this.inner_walls_count = 20;

        this.walls = []; //墙体数组
    }

    check_connectivity(g, sx, sy, tx, ty) { //dfs算法检测是否联通
        if (sx == tx && sy == ty) return true;
        g[sx][sy] = true;

        let dx = [-1, 1, 0, 0], dy = [0, 0, -1, 1];
        for (let i = 0; i < 4; i ++ ) {
            let x = sx + dx[i], y = sy + dy[i];
            if (!g[x][y] && this.check_connectivity(g, x, y, tx, ty)) //不是墙体或者可以搜到终点
                return true;
        }

        return false;
    }

    create_walls() {
        const g = [];
        //初始化
        for (let r = 0; r < this.rows; r ++ ) { //每一行
            g[r] = [];
            for (let c = 0; c < this.cols; c ++ ) { // 每一列
                g[r][c] = false; //无墙体为false
            }
        }

        //给四周加上障碍物
        for (let r = 0; r < this.rows; r ++ ) {
            g[r][0] = g[r][this.cols - 1] = true;
        }

        for (let c = 0; c < this.cols; c ++ ) {
            g[0][c] = g[this.rows - 1][c] = true;
        }



        //创建随机障碍物
        for (let i = 0; i < this.inner_walls_count / 2; i ++ ) {
            for (let j = 0; j < 1000; j ++ ) { //找到不重复的格子
                let r = parseInt(Math.random() * this.rows);
                let c = parseInt(Math.random() * this.cols);
                if (g[r][c] || g[c][r]) continue;
                if (r == this.rows - 2 && c == 1 || r == 1 && c == this.cols - 2)
                    continue; //出生点不能是墙体

                g[r][c] = g[c][r] = true;
                break;
            }
        }

        const copy_g = JSON.parse(JSON.stringify(g)); //先复制以下，防止被函数修改
        if (!this.check_connectivity(copy_g, this.rows - 2, 1, 1, this.cols - 2))//起点和终点的横纵坐标
            return false;



        //创建墙体    
        for (let r = 0; r < this.rows; r ++ ) {
            for (let c = 0; c < this.cols; c ++ ) {
                if (g[r][c]) {
                    this.walls.push(new Wall(r, c, this)); //创建墙体
                }
            }
        }

        return true;
    }

    start() {
        for (let i = 0; i < 1000; i ++ ) 
            if (this.create_walls()) //创建1000次墙体，直到找到满意的
                break;
    }

    update_size() {
        //动态更新边长，两种情况与一个最小值
        this.L = parseInt(Math.min(this.parent.clientWidth / this.cols, this.parent.clientHeight / this.rows));
        this.ctx.canvas.width = this.L * this.cols; //动态宽度
        this.ctx.canvas.height = this.L * this.rows; //动态长度
    }

    update() {
        this.update_size();
        this.render();
    }

    render() {
        //画出地图
        const color_even = "#AAD751", color_odd = "#A2D149"; //深绿和浅绿色
        for (let r = 0; r < this.rows; r ++ ) {
            for (let c = 0; c < this.cols; c ++ ) {
                if ((r + c) % 2 == 0) {
                    this.ctx.fillStyle = color_even;
                } else {
                    this.ctx.fillStyle = color_odd;
                }
                this.ctx.fillRect(c * this.L, r * this.L, this.L, this.L);
            }
        }
    }
}

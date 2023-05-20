import { AcGameObject } from "./AcGameObject";


//生成墙体的组件(一个小块)
export class Wall extends AcGameObject {
    constructor(r, c, gamemap) {
        super();

        this.r = r;
        this.c = c;
        this.gamemap = gamemap;
        this.color = "#740f0f";
    }

    update() {
        this.render();
    }

    render() { //渲染
        const L = this.gamemap.L;
        const ctx = this.gamemap.ctx;

        ctx.fillStyle = this.color;
        ctx.fillRect(this.c * L, this.r * L, L, L);
    }
}

const AC_GAME_OBJECTS = []; // 存储游戏对象

export class AcGameObject {  //基类
    constructor() {
        AC_GAME_OBJECTS.push(this); //加入游戏对象数组
        this.timedelta = 0; //每帧之间的时间间隔
        this.has_called_start = false; //判断有没有执行过start和update
    }

    start() {  // 只执行一次
    }

    update() {  // 每一帧执行一次，除了第一帧之外

    }

    on_destroy() {  // 删除之前执行 ，在destroy中调用即可

    }

    destroy() {
        this.on_destroy();

        for (let i in AC_GAME_OBJECTS) {
            const obj = AC_GAME_OBJECTS[i];
            if (obj === this) {  //删除这个游戏对象
                AC_GAME_OBJECTS.splice(i);
                break;
            }
        }
    }
}

let last_timestamp;  // 上一次执行的时刻
const step = timestamp => { //当前时刻
    for (let obj of AC_GAME_OBJECTS) { //of 遍历值  in 遍历下标
        if (!obj.has_called_start) {
            obj.has_called_start = true;
            obj.start();
        } else {
            obj.timedelta = timestamp - last_timestamp;
            obj.update();
        }
    }

    last_timestamp = timestamp;
    requestAnimationFrame(step) //下一帧 递归调用
}

requestAnimationFrame(step) //控制刷新，在下一帧渲染之前，调用一次step

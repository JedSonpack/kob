import { vi } from "vitest";

// AcGameObject 在模块加载时调用 requestAnimationFrame 启动渲染循环；
// 测试中用空实现避免无限 rAF 与定时器噪声。
vi.stubGlobal("requestAnimationFrame", vi.fn(() => 0));

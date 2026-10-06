/**
 * Anvil's Search 动画控制器。
 *
 * <p>按"面板级 / 组件级"划分，每个控制器封装一类动画的 key 命名与读写语义。
 * 渲染层只调用控制器方法，不直接触碰 {@code AnimationManager} 的字符串 key。
 *
 * <ul>
 *   <li>{@link top.leipishu.anvilssearch.client.animation.controller.AnvilPanelAnimations} —
 *       面板滑入滑出 / 宽度 / Tab 指示器 / Tab hover / 侧边按钮 hover</li>
 *   <li>{@link top.leipishu.anvilssearch.client.animation.controller.AnvilWidgetAnimations} —
 *       行 hover / 行选中 / 卡片展开 / 卡片淡入 / 星标脉冲 / 按钮 hover /
 *       槽位状态色 / Popup 出现消失 / 反馈文本淡入</li>
 * </ul>
 */
package top.leipishu.anvilssearch.client.animation.controller;
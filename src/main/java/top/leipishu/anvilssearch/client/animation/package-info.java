/**
 * Anvil's Search 动画接入层。
 *
 * <p>复用 Tinkers' Search 的 {@code top.leipishu.tinkerssearch.client.animation.core}
 * 动画基础设施（{@code AnimationManager} / {@code Animation} / {@code Animator} /
 * {@code Easing} / {@code ColorUtil}），不重复造轮子。
 *
 * <p>本包只提供：
 * <ul>
 *   <li>{@link top.leipishu.anvilssearch.client.animation.AnvilAnimations} —
 *       key 前缀常量与通用清理工具</li>
 *   <li>{@code controller} 子包 — 按 UI 模块分组的动画读写封装</li>
 * </ul>
 *
 * <p>驱动由 Tinkers' Search 的 {@code TinkersSearch.onRenderTick} 每帧调用
 * {@code AnimationManager.update()} 完成，Anvil's Search 不重复挂事件。
 */
package top.leipishu.anvilssearch.client.animation;
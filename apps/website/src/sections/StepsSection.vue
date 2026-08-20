<template>
  <section class="steps">
    <div class="container">
      <h2>三步开始</h2>
      <ol class="list">
        <li>
          <h3>下载安装</h3>
          <p>选择你的平台，安装包直接从 GitHub Releases 获取。</p>
        </li>
        <li>
          <h3>登录账号</h3>
          <p>在应用里点登录，浏览器完成认证后自动跳回；首次登录即完成注册。</p>
        </li>
        <li>
          <h3>开始编码</h3>
          <p>链路接入、席位配好之后终端就绪，像本地终端一样使用。</p>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.steps {
  padding: 88px 0;
  background: var(--bg-soft);
  border-top: 1px solid var(--border);
}

h2 {
  font-size: clamp(26px, 3.6vw, 36px);
  margin-bottom: 40px;
}

/* 与上一节的三格网格换一种版式：带序号的横向流程 */
.list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 40px;
  counter-reset: step;
}

.list li {
  counter-increment: step;
  padding-top: 18px;
  border-top: 2px solid var(--brand);
  position: relative;
}

.list li::before {
  content: counter(step);
  font-family: var(--font-mono);
  font-weight: 500;
  color: var(--brand-text);
  display: block;
  margin-bottom: 8px;
}

h3 {
  font-size: 19px;
  margin-bottom: 8px;
}

.list p {
  color: var(--ink-2);
  font-size: 15px;
}

@media (max-width: 768px) {
  .list {
    grid-template-columns: 1fr;
    gap: 28px;
  }
}
</style>

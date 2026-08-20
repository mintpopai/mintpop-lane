<template>
  <section id="features" class="features">
    <div class="container">
      <h2>不用配环境，也不用担心账号</h2>
      <div class="grid">
        <article class="cell wide">
          <h3>专属链路，一人一路</h3>
          <p>
            每个账号走独立的出网链路与固定出口，由服务端统一管理。
            不与他人混用出口，从源头降低共享代理带来的账号风控风险。
          </p>
        </article>
        <article class="cell">
          <h3>开箱即用</h3>
          <p>代理、密钥、运行环境全部自动配置，登录完成即进入可用的终端。</p>
        </article>
        <article class="cell">
          <h3>订阅即席位</h3>
          <p>购买的 Agent 服务与账号绑定，到期与换绑由服务端处理，客户端无感。</p>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.features {
  padding: 88px 0;
  border-top: 1px solid var(--border);
}

h2 {
  font-size: clamp(26px, 3.6vw, 36px);
  margin-bottom: 40px;
}

/* 1 宽 + 2 窄的非对称三格：刚好三条内容三个格 */
.grid {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  grid-template-rows: auto auto;
  gap: 18px;
}

.cell {
  border: 1px solid var(--border);
  border-radius: var(--radius);
  padding: 28px;
  background: var(--bg-soft);
}

.cell.wide {
  grid-row: span 2;
  /* 主格给品牌色底，避免整片白格白底 */
  background: linear-gradient(160deg, var(--bg-mint) 0%, #ffffff 90%);
  border-color: #cdeee3;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

h3 {
  font-size: 20px;
  margin-bottom: 10px;
}

.cell p {
  color: var(--ink-2);
  font-size: 15.5px;
}

@media (max-width: 768px) {
  .grid {
    grid-template-columns: 1fr;
    grid-template-rows: none;
  }
  .cell.wide {
    grid-row: auto;
  }
}
</style>

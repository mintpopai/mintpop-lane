<script setup lang="ts">
import { verify } from "../content/copy";
</script>

<template>
  <section id="verify" class="section section-line">
    <div class="container">
      <p class="kicker">{{ verify.kicker }}</p>
      <h2 class="section-title">{{ verify.title }}</h2>
      <p class="section-lede">{{ verify.lede }}</p>

      <p class="caption">{{ verify.caption }}</p>

      <!-- 左右对照：同一个故障，别处给你一行，Lane 给你一整段。
           两张卡等高，左边那片留白就是论据本身，别把它填满。 -->
      <div class="pair">
        <article class="card plain">
          <span class="tag">{{ verify.others.tag }}</span>
          <p class="code">{{ verify.others.code }}</p>
          <p class="note">{{ verify.others.note }}</p>
        </article>

        <article class="card ours">
          <span class="tag tag-brand">{{ verify.lane.tag }}</span>
          <span class="badge">{{ verify.lane.badge }}</span>
          <h3 class="label">{{ verify.lane.label }}</h3>
          <p class="detail">{{ verify.lane.detail }}</p>
          <!-- 画成应用里那个按钮的样子，但它只是展示，不可点 -->
          <span class="retry">{{ verify.lane.retry }}</span>
          <p class="note">{{ verify.lane.note }}</p>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.caption {
  margin-top: 40px;
  font-size: 13px;
  color: var(--ink-3);
  font-family: var(--font-mono);
}

.pair {
  margin-top: 14px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
  align-items: stretch;
}

.card {
  display: flex;
  flex-direction: column;
  min-height: 300px;
  padding: 22px 22px 20px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--bg);
  border-left-width: 3px;
  border-left-color: var(--line);
}

/* 对照组往后退一层：底色压暗、不抢视线，右边那张才是要看的 */
.card.plain {
  background: var(--bg-soft);
}

.card.ours {
  border-left-color: var(--warn);
}

.tag {
  align-self: flex-start;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--ink-3);
}

.tag-brand {
  color: var(--brand-text);
}

/* —— 左：能拿到的全部信息就这一行 —— */
.code {
  margin-top: 26px;
  font-family: var(--font-mono);
  font-size: 15px;
  color: var(--danger);
}

/* —— 右：状态、说清楚的原因、下一步 —— */
.badge {
  align-self: flex-start;
  margin-top: 18px;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 600;
  background: var(--warn-soft);
  color: var(--warn);
}

.label {
  margin-top: 12px;
  font-size: 19px;
}

.detail {
  margin-top: 8px;
  font-size: 14.5px;
  color: var(--ink-2);
}

.retry {
  align-self: flex-start;
  margin-top: 18px;
  padding: 7px 15px;
  border-radius: var(--radius-button);
  border: 1px solid var(--line);
  background: var(--bg-soft);
  font-size: 13px;
  font-weight: 500;
  color: var(--ink);
}

.note {
  margin-top: auto;
  padding-top: 22px;
  font-size: 13px;
  color: var(--ink-3);
}

@media (max-width: 900px) {
  .pair {
    grid-template-columns: 1fr;
  }
  .card {
    min-height: 0;
  }
  .note {
    padding-top: 26px;
  }
}
</style>

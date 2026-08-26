<script setup lang="ts">
import { useI18n } from "../i18n";

const { t } = useI18n();
</script>

<template>
  <section id="pricing" class="section section-line">
    <div class="container">
      <p class="kicker">{{ t.pricing.kicker }}</p>
      <h2 class="section-title">{{ t.pricing.title }}</h2>
      <p class="section-lede">{{ t.pricing.lede }}</p>

      <!-- 两档并列、等高：差别只在用量规格，所以两张卡的行序完全一致，
           同一行横着看就是同一项在两档下的取值。主推档（第二张）靠底色与边框加重，不靠加长内容。 -->
      <div class="plans">
        <article
          v-for="(plan, i) in t.pricing.plans"
          :key="plan.name"
          class="plan"
          :class="{ featured: i === t.pricing.plans.length - 1 }"
        >
          <div class="head">
            <h3 class="name">{{ plan.name }}</h3>
            <span class="badge">{{ plan.badge }}</span>
          </div>
          <p class="price">
            <span class="amount">{{ plan.price }}</span>
            <!-- 原价划线：<s> 而非纯样式，读屏也能知道这个价已作废 -->
            <s class="was mono">{{ plan.was }}</s>
          </p>
          <p class="quota-label">{{ plan.quotaLabel }}</p>
          <p class="quota">{{ plan.quota }}</p>
          <p class="fit">{{ plan.fit }}</p>
        </article>
      </div>

      <div class="guarantee">
        <h3 class="guarantee-title">{{ t.pricing.guarantee.title }}</h3>
        <p class="guarantee-body">{{ t.pricing.guarantee.body }}</p>
      </div>

      <!-- 两档共同的保障：不放进卡里逐档重复一遍，抽出来排成 dl 说一次 -->
      <dl class="assurances">
        <template v-for="a in t.pricing.assurances" :key="a.term">
          <dt>{{ a.term }}</dt>
          <dd>{{ a.body }}</dd>
        </template>
      </dl>
    </div>
  </section>
</template>

<style scoped>
.plans {
  margin-top: 40px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
  max-width: 880px;
  align-items: stretch;
}

.plan {
  display: flex;
  flex-direction: column;
  padding: 32px;
  background: var(--bg);
  border: 1px solid var(--line);
  border-radius: var(--radius);
}

.plan.featured {
  background: linear-gradient(160deg, var(--bg-mint) 0%, var(--bg) 62%);
  border-color: var(--mint-deep);
  box-shadow: 0 18px 40px -28px rgba(11, 11, 12, 0.35);
}

.head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.name {
  font-size: 22px;
}

.badge {
  margin-left: auto;
  padding: 3px 10px;
  border-radius: var(--radius-pill);
  font-size: 12px;
  font-weight: 500;
  border: 1px solid var(--line);
  background: var(--bg-soft);
  color: var(--ink-3);
}

.plan.featured .badge {
  border-color: var(--line-mint);
  background: rgba(255, 255, 255, 0.7);
  color: var(--brand-strong);
}

.price {
  margin-top: 22px;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.amount {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 44px;
  line-height: 1;
  letter-spacing: -0.02em;
}

.was {
  font-size: 14px;
  color: var(--ink-3);
}

/* 分隔线之下是「用量规格」这一档的取值，两张卡在同一水平线上对齐 */
.quota-label {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px solid var(--line);
  font-size: 13px;
  color: var(--ink-3);
}

.plan.featured .quota-label {
  border-top-color: var(--line-mint);
}

.quota {
  margin-top: 6px;
  font-size: 16px;
  color: var(--ink);
}

/* margin-top:auto 把适用场景压到卡底，两张卡不等长时底边仍然齐平 */
.fit {
  margin-top: auto;
  padding-top: 24px;
  font-size: 14px;
  color: var(--ink-2);
}

/* 售后承诺是钱上的承诺，用琥珀色竖条把它从两张卡里拎出来单说 */
.guarantee {
  margin-top: 20px;
  max-width: 880px;
  padding: 22px 24px;
  border: 1px solid var(--line);
  border-left: 3px solid var(--warn);
  border-radius: var(--radius);
  background: var(--bg-soft);
}

.guarantee-title {
  font-size: 17px;
}

.guarantee-body {
  margin-top: 8px;
  font-size: 14.5px;
  color: var(--ink-2);
}

.assurances {
  margin: 36px 0 0;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 10px 20px;
  max-width: 880px;
  font-size: 14px;
}

.assurances dt {
  font-weight: 600;
  color: var(--ink);
}

.assurances dd {
  margin: 0;
  color: var(--ink-2);
}

@media (max-width: 760px) {
  .plans {
    grid-template-columns: 1fr;
  }
}

/* 窄屏上术语列（尤其英文的 "Verifiably official"）会把说明挤成一条窄缝，改上下排 */
@media (max-width: 560px) {
  .assurances {
    grid-template-columns: 1fr;
    gap: 4px 0;
  }
  .assurances dd {
    margin-bottom: 14px;
  }
  .assurances dd:last-child {
    margin-bottom: 0;
  }
}
</style>

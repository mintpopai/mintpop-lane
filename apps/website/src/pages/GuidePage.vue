<script setup lang="ts">
import { computed } from "vue";
import { useHead } from "@unhead/vue";
import { useRoute } from "vue-router";
import { findGuide } from "../content/guides";
import { localePath, useI18n } from "../i18n";
import { SITE, guidePath } from "../routes";

const route = useRoute();
const { locale, t } = useI18n();

// slug 来自路由参数；只有注册表里的 slug 会被预渲染，线上未知 slug 由 nginx 返 404，
// 这里的 v-if 只是防御（客户端不可能经站内链接走到未知 slug）。
const guide = computed(() => findGuide(String(route.params.slug)));
const copy = computed(() => guide.value?.copy[locale.value]);
const pageUrl = computed(() => (guide.value ? SITE + guidePath(locale.value, guide.value.slug) : SITE));
const homePath = computed(() => localePath(locale.value));

// 指南页自己的 head：标题、描述、社交卡片文字，
// 以及三段结构化数据：Article（正文）、BreadcrumbList（首页 › 本篇）、FAQPage（页内问答）。
// 路径相关项（canonical / hreflang / og:url / og:image）在 App.vue 统一输出；
// og:type 沿用 index.html 里静态的 website，不再另输出 article（两条 og:type 会打架）。
useHead({
  title: () => copy.value?.meta.title ?? "",
  meta: () => {
    if (!copy.value) return [];
    const { title, description } = copy.value.meta;
    return [
      { name: "description", content: description },
      { property: "og:title", content: title },
      { property: "og:description", content: description },
      { property: "og:image:alt", content: title },
      { name: "twitter:title", content: title },
      { name: "twitter:description", content: description },
    ];
  },
  script: () => {
    if (!copy.value) return [];
    const c = copy.value;
    const org = { "@type": "Organization", name: "MintPop", url: "https://mintpop.ai" };
    const inLanguage = locale.value === "zh" ? "zh-CN" : "en";
    return [
      {
        type: "application/ld+json",
        innerHTML: JSON.stringify({
          "@context": "https://schema.org",
          "@type": "Article",
          headline: c.h1,
          description: c.meta.description,
          inLanguage,
          datePublished: c.published,
          dateModified: c.updated,
          mainEntityOfPage: pageUrl.value,
          image: `${SITE}/${locale.value === "zh" ? "og.png" : "og-en.png"}`,
          author: org,
          publisher: {
            ...org,
            logo: {
              "@type": "ImageObject",
              url: "https://standards.mintpop.ai/assets/products/lane/lane-app-cloud.png",
            },
          },
        }),
      },
      {
        type: "application/ld+json",
        innerHTML: JSON.stringify({
          "@context": "https://schema.org",
          "@type": "BreadcrumbList",
          itemListElement: [
            { "@type": "ListItem", position: 1, name: t.value.ui.guide.home, item: SITE + homePath.value },
            { "@type": "ListItem", position: 2, name: c.h1, item: pageUrl.value },
          ],
        }),
      },
      {
        type: "application/ld+json",
        innerHTML: JSON.stringify({
          "@context": "https://schema.org",
          "@type": "FAQPage",
          inLanguage,
          mainEntity: c.faq.map((f) => ({
            "@type": "Question",
            name: f.q,
            acceptedAnswer: { "@type": "Answer", text: f.a },
          })),
        }),
      },
    ];
  },
});
</script>

<template>
  <main v-if="copy" class="guide">
    <article class="container">
      <nav class="crumbs" aria-label="breadcrumb">
        <RouterLink :to="homePath">{{ t.ui.guide.home }}</RouterLink>
        <span aria-hidden="true">›</span>
        <span>{{ copy.navLabel }}</span>
      </nav>

      <p class="kicker">{{ copy.kicker }}</p>
      <h1>{{ copy.h1 }}</h1>
      <p class="intro">{{ copy.intro }}</p>
      <p class="updated">
        {{ t.ui.guide.updated }} <time :datetime="copy.updated">{{ copy.updated }}</time>
      </p>

      <section v-for="s in copy.sections" :key="s.heading" class="block">
        <h2>{{ s.heading }}</h2>
        <p v-for="p in s.paragraphs" :key="p">{{ p }}</p>
        <ul v-if="s.bullets">
          <li v-for="b in s.bullets" :key="b">{{ b }}</li>
        </ul>
      </section>

      <!-- 页内 FAQ：与首页同款 details，无 JS、键盘可达、爬虫可读 -->
      <section class="block faq">
        <h2>{{ t.ui.guide.faqTitle }}</h2>
        <details v-for="f in copy.faq" :key="f.q">
          <summary>{{ f.q }}</summary>
          <p>{{ f.a }}</p>
        </details>
      </section>

      <aside class="cta">
        <h2>{{ copy.cta.title }}</h2>
        <p>{{ copy.cta.body }}</p>
        <a class="btn btn-primary" :href="`${homePath}#download`">{{ copy.cta.label }}</a>
      </aside>
    </article>
  </main>
</template>

<style scoped>
/* 单列长文：版心收窄到 760px，行长控制在 40 字上下，与首页各区块的 container 同一套左右留白 */
.guide {
  padding: 56px 0 96px;
}

.guide .container {
  max-width: 760px;
}

.crumbs {
  display: flex;
  gap: 10px;
  font-size: 14px;
  color: var(--ink-3);
  margin-bottom: 28px;
}

.crumbs a {
  text-decoration: none;
}

.crumbs a:hover {
  color: var(--brand-text);
}

h1 {
  font-size: clamp(30px, 4.6vw, 44px);
  line-height: 1.15;
}

.intro {
  margin-top: 20px;
  font-size: 19px;
  line-height: 1.6;
  color: var(--ink);
}

.updated {
  margin-top: 14px;
  font-family: var(--font-mono);
  font-size: 13px;
  color: var(--ink-3);
}

.block {
  margin-top: 48px;
}

.block h2 {
  font-size: 24px;
  margin-bottom: 14px;
}

.block p {
  margin-top: 12px;
  font-size: 16.5px;
  line-height: 1.75;
  color: var(--ink-2);
}

.block ul {
  margin: 14px 0 0;
  padding-left: 22px;
}

.block li {
  margin-top: 10px;
  font-size: 16.5px;
  line-height: 1.7;
  color: var(--ink-2);
}

.faq details {
  border-top: 1px solid var(--line);
}

.faq details:last-of-type {
  border-bottom: 1px solid var(--line);
}

.faq summary {
  padding: 16px 0;
  cursor: pointer;
  font-family: var(--font-display);
  font-weight: 500;
  font-size: 17px;
}

.faq details[open] summary {
  color: var(--brand-text);
}

.faq details p {
  margin: 0 0 18px;
}

.cta {
  margin-top: 56px;
  padding: 32px;
  border-radius: var(--radius);
  background: var(--bg-mint);
  border: 1px solid var(--line-mint);
}

.cta h2 {
  font-size: 24px;
}

.cta p {
  margin: 10px 0 20px;
  color: var(--ink-2);
}

@media (max-width: 600px) {
  .guide {
    padding-top: 32px;
  }

  .cta {
    padding: 24px;
  }
}
</style>

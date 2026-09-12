<script setup lang="ts">
import { useHead } from "@unhead/vue";
import HeroSection from "../sections/HeroSection.vue";
import PricingSection from "../sections/PricingSection.vue";
import LaneSection from "../sections/LaneSection.vue";
import VerifySection from "../sections/VerifySection.vue";
import TerminalSection from "../sections/TerminalSection.vue";
import StepsSection from "../sections/StepsSection.vue";
import DownloadSection from "../sections/DownloadSection.vue";
import GuidesSection from "../sections/GuidesSection.vue";
import FaqSection from "../sections/FaqSection.vue";
import { localePath, useI18n } from "../i18n";
import { SITE } from "../routes";

const { locale, t } = useI18n();

// 首页自己的 head：标题、描述、社交卡片文字、SoftwareApplication 结构化数据。
// 路径相关项（canonical / hreflang / og:url / og:image）在 App.vue 统一输出。
useHead({
  title: () => t.value.meta.title,
  meta: () => {
    const { title, description } = t.value.meta;
    return [
      { name: "description", content: description },
      { property: "og:title", content: title },
      { property: "og:description", content: description },
      { property: "og:image:alt", content: title },
      { name: "twitter:title", content: title },
      { name: "twitter:description", content: description },
    ];
  },
  script: () => [
    {
      type: "application/ld+json",
      innerHTML: JSON.stringify({
        "@context": "https://schema.org",
        "@type": "SoftwareApplication",
        name: "MintPop Lane",
        applicationCategory: "DeveloperApplication",
        operatingSystem: "macOS (Apple silicon), Windows 10/11 x64",
        url: SITE + localePath(locale.value),
        downloadUrl: `${SITE}${localePath(locale.value)}#download`,
        description: t.value.meta.description,
        inLanguage: locale.value === "zh" ? "zh-CN" : "en",
        author: { "@type": "Organization", name: "MintPop", url: "https://mintpop.ai" },
      }),
    },
  ],
});
</script>

<template>
  <main>
    <HeroSection />
    <!-- 叙事顺序：多少钱（席位与价格）→ 省了什么事（专属链路）→ 出问题时怎么办 → 怎么用 → 三步 → 下载 → 延伸阅读 → FAQ -->
    <PricingSection />
    <LaneSection />
    <VerifySection />
    <TerminalSection />
    <StepsSection />
    <DownloadSection />
    <GuidesSection />
    <FaqSection />
  </main>
</template>

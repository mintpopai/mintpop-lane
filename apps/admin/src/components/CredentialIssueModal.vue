<script setup lang="ts">
// Claude 席位凭证签发：服务端经该席位自己的落地代理出口，向 Anthropic 发起 OAuth 授权。
// 三步流程：① 发起 → ② 贴回授权码兑换 → ③ 完成。
// 安全要求：授权码与 sessionId 只留在本组件的响应式状态里，不 console.log、
// 不落 localStorage/sessionStorage，随组件卸载（modal 关闭）整份销毁。
import { ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type { AdminSubscriptionResponse, CredentialAuthorizationStart, CredentialIssueResult } from "../api/types";
import { showToast } from "../toast";
import { formatDateTime } from "../utils/format";
import Modal from "./AdminModal.vue";

const props = defineProps<{ subscription: AdminSubscriptionResponse }>();
const emit = defineEmits<{ close: []; issued: [] }>();

const step = ref<"start" | "authorize" | "done">("start");
const authorizing = ref(false);
const exchanging = ref(false);
const authStart = ref<CredentialAuthorizationStart | null>(null);
const code = ref("");
const result = ref<CredentialIssueResult | null>(null);

function reportError(error: unknown, prefix: string): void {
  showToast("error", error instanceof BizError ? error.message : `${prefix}：${(error as Error).message}`);
}

async function startAuthorize(): Promise<void> {
  authorizing.value = true;
  try {
    authStart.value = await adminApi().credentialAuthorizeUrl(props.subscription.id);
    step.value = "authorize";
  } catch (error) {
    reportError(error, "发起签发失败");
  } finally {
    authorizing.value = false;
  }
}

async function copyAuthUrl(): Promise<void> {
  if (!authStart.value) {
    return;
  }
  try {
    await navigator.clipboard.writeText(authStart.value.authUrl);
    showToast("success", "授权链接已复制");
  } catch {
    showToast("error", "复制失败，请手动选中复制");
  }
}

function openAuthUrl(): void {
  if (!authStart.value) {
    return;
  }
  window.open(authStart.value.authUrl, "_blank", "noopener,noreferrer");
}

async function exchange(): Promise<void> {
  if (!authStart.value) {
    return;
  }
  const trimmedCode = code.value.trim();
  if (!trimmedCode) {
    showToast("error", "先贴入授权码");
    return;
  }
  exchanging.value = true;
  try {
    result.value = await adminApi().credentialExchange(props.subscription.id, {
      sessionId: authStart.value.sessionId,
      code: trimmedCode,
    });
    // 兑换完成即清空授权码与会话态，不在内存里多留一秒
    code.value = "";
    authStart.value = null;
    step.value = "done";
    showToast("success", "凭证已签发");
  } catch (error) {
    reportError(error, "兑换失败");
  } finally {
    exchanging.value = false;
  }
}

function close(): void {
  if (step.value === "done") {
    emit("issued");
    return;
  }
  emit("close");
}
</script>

<template>
  <Modal title="签发 Claude 席位凭证" @close="close()">
    <div class="issue-body">
      <template v-if="step === 'start'">
        <p class="admin-note">
          将由服务端经该席位自己的落地代理出口，向 Anthropic 发起一次 OAuth 授权，
          签出一份仅 <span class="fact">user:profile</span> 权限、有效期跟随订阅剩余时长的凭证。
        </p>
        <dl class="issue-facts">
          <div class="issue-fact">
            <dt>订阅</dt>
            <dd>{{ subscription.name }}（分配号 {{ subscription.assignmentNo }}）</dd>
          </div>
          <div class="issue-fact">
            <dt>当前录入的账号邮箱</dt>
            <dd :class="{ fact: subscription.accountEmail !== null }">
              {{ subscription.accountEmail ?? "未录入" }}
            </dd>
          </div>
        </dl>
      </template>

      <template v-else-if="step === 'authorize' && authStart">
        <!-- 最醒目位置：将要登录的账号。这一步签错账号不会报任何错，只会签出一份错账号的凭证 -->
        <div class="issue-warn">
          <p class="issue-warn-title">即将登录的账号</p>
          <p class="issue-warn-email">
            {{ authStart.accountEmail ?? "该订阅未录入账号邮箱，请自行确认要授权的是哪个账号" }}
          </p>
          <p class="issue-warn-hint">
            建议用浏览器<strong>隐身窗口</strong>打开下面的授权链接——你的常规浏览器里大概率登着自己的账号，
            用错窗口打开会直接把自己的账号签进这份凭证。
          </p>
        </div>

        <div class="admin-field">
          <label for="issue-egress-ip">落地出口 IP</label>
          <input id="issue-egress-ip" class="admin-input fact" :value="authStart.egressIp" disabled />
          <p class="admin-note">核对这个出口 IP 与该席位实际使用的一致，再继续授权。</p>
        </div>

        <div class="admin-field">
          <label for="issue-auth-url">授权链接</label>
          <div class="issue-url-row">
            <input id="issue-auth-url" class="admin-input fact" :value="authStart.authUrl" readonly />
            <button type="button" class="admin-btn-ghost" @click="copyAuthUrl()">复制</button>
            <button type="button" class="admin-btn-ghost" @click="openAuthUrl()">新窗口打开</button>
          </div>
        </div>

        <div class="admin-field">
          <label for="issue-code">授权码</label>
          <input
            id="issue-code"
            v-model="code"
            class="admin-input"
            placeholder="从授权页跳转后贴回的授权码"
            autocomplete="off"
            spellcheck="false"
          />
        </div>
      </template>

      <template v-else-if="step === 'done' && result">
        <p class="admin-note">凭证已签发并直接生效，无需再做其它操作。</p>
        <dl class="issue-facts">
          <div class="issue-fact">
            <dt>账号邮箱</dt>
            <dd :class="{ fact: result.accountEmail !== null }">{{ result.accountEmail ?? "未知" }}</dd>
          </div>
          <div class="issue-fact">
            <dt>授权范围</dt>
            <dd class="fact">{{ result.grantedScope }}</dd>
          </div>
          <div class="issue-fact">
            <dt>到期时刻</dt>
            <dd class="fact">{{ formatDateTime(result.expiresAt) }}</dd>
          </div>
        </dl>
      </template>
    </div>

    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="close()">
        {{ step === "done" ? "关闭" : "取消" }}
      </button>
      <button
        v-if="step === 'start'"
        type="button"
        class="admin-btn"
        :disabled="authorizing"
        @click="startAuthorize()"
      >
        {{ authorizing ? "生成中…" : "生成授权链接" }}
      </button>
      <button v-else-if="step === 'authorize'" type="button" class="admin-btn" :disabled="exchanging" @click="exchange()">
        {{ exchanging ? "签发中…" : "完成签发" }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.issue-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.issue-facts {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.issue-fact {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.issue-fact dt {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.issue-fact dd {
  font-size: 14px;
  color: var(--color-ink);
}

/* 全场最醒目的一块：账号核对，签错不报错、只会签出错账号的凭证。
   沿用「凭据未录入」状态点同一个琥珀色——都是「需要人停下来确认」的语义 */
.issue-warn {
  padding: 16px 20px;
  border-radius: var(--radius-card);
  border: 1px solid color-mix(in srgb, #b4720b 35%, var(--color-border));
  background: color-mix(in srgb, #b4720b 10%, #ffffff);
}

.issue-warn-title {
  font-size: 12px;
  color: var(--color-ink-secondary);
}

.issue-warn-email {
  margin-top: 4px;
  font-family: var(--font-fact);
  font-size: 18px;
  font-weight: 700;
  color: var(--color-ink);
}

.issue-warn-hint {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-ink-secondary);
}

.issue-url-row {
  display: flex;
  gap: 8px;
}

.issue-url-row .admin-input {
  flex: 1;
  min-width: 0;
}

.issue-url-row .admin-btn-ghost {
  flex-shrink: 0;
  white-space: nowrap;
}
</style>

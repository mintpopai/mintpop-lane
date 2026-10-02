<script setup lang="ts">
// 手动分配第一跳：管理员按顺位挑机场订阅（第 0 个主用，其余备用），越过自动分配算法。
// 规则与服务端一致：每家机场最多一项、主用只能来自主用机场、订阅要有节点；名额满了照样允许，只提示超额
import { computed, onMounted, ref } from "vue";
import { adminApi } from "../api";
import { BizError } from "../api/http";
import type {
  AirportResponse,
  AirportSubscriptionResponse,
  FrontSubscriptionBrief,
} from "../api/types";
import { showToast } from "../toast";
import Modal from "./AdminModal.vue";
import Select from "./AdminSelect.vue";

/** 与服务端 FrontSettings.MAX_AIRPORTS_PER_USER 同值 */
const MAX_ROWS = 10;

const props = defineProps<{ userId: number; current: FrontSubscriptionBrief[] }>();
// 弹窗由父组件 v-if 挂载/卸载，打开即按当前列表初始化
const emit = defineEmits<{ close: []; saved: [] }>();

const loading = ref(true);
const loadError = ref("");
const airports = ref<AirportResponse[]>([]);
const subscriptions = ref<AirportSubscriptionResponse[]>([]);
/** 每行选中的订阅 id，下标即顺位；未选为 null */
const rows = ref<(number | null)[]>(
  props.current.length > 0 ? props.current.map((c) => c.airportSubscriptionId) : [null],
);
const submitting = ref(false);

/** 该用户眼下的主用订阅：算名额时要把他自己那一个减掉，否则重存原主用会被误报超额 */
const currentPrimaryId = props.current[0]?.airportSubscriptionId ?? null;

const primaryAirportIds = computed(
  () => new Set(airports.value.filter((a) => a.primaryEnabled).map((a) => a.id)),
);

const byId = computed(() => new Map(subscriptions.value.map((s) => [s.id, s])));

/** 除他自己以外已占的主用人数 */
function usedByOthers(sub: AirportSubscriptionResponse): number {
  return sub.primaryUsed - (sub.id === currentPrimaryId ? 1 : 0);
}

function rowLabel(index: number): string {
  return index === 0 ? "主用" : `备用${index}`;
}

/** 某一行可选的订阅：有节点、所属机场没被其它行占用；主用行只列主用机场 */
function optionsFor(index: number) {
  const takenAirports = new Set(
    rows.value
      .filter((id, i) => i !== index && id !== null)
      .map((id) => byId.value.get(id as number)?.airportId),
  );
  return subscriptions.value
    .filter((s) => s.nodeCount > 0)
    .filter((s) => !takenAirports.has(s.airportId))
    .filter((s) => index !== 0 || primaryAirportIds.value.has(s.airportId))
    .map((s) => ({
      value: s.id,
      label:
        index === 0
          ? `${s.airportName} · ${s.name}（主用 ${usedByOthers(s)}/${s.primaryCapacity}）`
          : `${s.airportName} · ${s.name}`,
    }));
}

/** 选中的主用已满：允许继续，但要说清楚会超额 */
const primaryOverflow = computed(() => {
  const id = rows.value[0];
  const sub = id === null || id === undefined ? undefined : byId.value.get(id);
  if (!sub || usedByOthers(sub) < sub.primaryCapacity) {
    return "";
  }
  return `「${sub.airportName} · ${sub.name}」主用名额已满（${usedByOthers(sub)}/${sub.primaryCapacity}），手动分配仍会占用，名额将超额。`;
});

/** 主用行原先选的机场被改成备用机场、或订阅已无节点时，下拉里没有它，提示重选 */
const staleRows = computed(() =>
  rows.value
    .map((id, i) => ({ id, i }))
    .filter(({ id, i }) => id !== null && !optionsFor(i).some((o) => o.value === id))
    .map(({ i }) => rowLabel(i)),
);

const canSubmit = computed(
  () =>
    !loading.value &&
    !submitting.value &&
    rows.value.every((id) => id !== null) &&
    staleRows.value.length === 0,
);

function addRow(): void {
  if (rows.value.length < MAX_ROWS) {
    rows.value.push(null);
  }
}

function removeRow(index: number): void {
  rows.value.splice(index, 1);
}

/** 与上一行互换：同一机场不能出现两次，靠重选没法对调主用与备用 */
function moveUp(index: number): void {
  const list = rows.value;
  [list[index - 1], list[index]] = [list[index], list[index - 1]];
}

async function load(): Promise<void> {
  try {
    const [a, s] = await Promise.all([
      adminApi().listAirports(),
      adminApi().listAirportSubscriptions(),
    ]);
    airports.value = a;
    subscriptions.value = s;
  } catch (error) {
    loadError.value = error instanceof BizError ? error.message : (error as Error).message;
  } finally {
    loading.value = false;
  }
}

async function submit(): Promise<void> {
  if (!canSubmit.value) {
    return;
  }
  submitting.value = true;
  try {
    await adminApi().assignUserFrontManually(props.userId, rows.value as number[]);
    showToast("success", "已手动分配机场订阅，用户下次建立链路时生效");
    emit("saved");
    emit("close");
  } catch (error) {
    // 410058 手动分配不合法等，服务端给的中文提示直接用
    showToast(
      "error",
      error instanceof BizError ? error.message : `分配失败：${(error as Error).message}`,
    );
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <Modal title="手动分配机场订阅" @close="emit('close')">
    <p v-if="loading" class="admin-hint">加载中……</p>
    <p v-else-if="loadError" class="admin-hint error">{{ loadError }}</p>
    <div v-else class="admin-form">
      <p class="admin-note">
        按顺位挑订阅：第一行是主用（只能选主用机场），其余是备用，每家机场只能出现一次。手动分配的用户在「重算全部线路」选择保留时不会被改动。
      </p>
      <div v-for="(_, index) in rows" :key="index" class="manual-row">
        <span class="pill">{{ rowLabel(index) }}</span>
        <Select
          v-model="rows[index]"
          class="manual-select"
          :options="optionsFor(index)"
          :aria-label="`${rowLabel(index)}订阅`"
        />
        <button
          type="button"
          class="admin-link"
          :disabled="index === 0"
          :aria-label="`${rowLabel(index)}上移`"
          @click="moveUp(index)"
        >
          上移
        </button>
        <button
          type="button"
          class="admin-link"
          :disabled="rows.length === 1"
          :aria-label="`删除${rowLabel(index)}`"
          @click="removeRow(index)"
        >
          删除
        </button>
      </div>
      <button
        type="button"
        class="admin-btn-ghost add-row"
        :disabled="rows.length >= MAX_ROWS"
        @click="addRow()"
      >
        添加备用
      </button>
      <p v-if="primaryOverflow" class="admin-note warn">{{ primaryOverflow }}</p>
      <p v-if="staleRows.length > 0" class="admin-note warn">
        {{
          staleRows.join("、")
        }}原先的订阅已不可选（所属机场改为备用机场、订阅没有节点或与其它行同一机场），请重选。
      </p>
    </div>

    <template #footer>
      <button type="button" class="admin-btn-ghost" @click="emit('close')">取消</button>
      <button type="button" class="admin-btn" :disabled="!canSubmit" @click="submit()">
        {{ submitting ? "保存中…" : "保存" }}
      </button>
    </template>
  </Modal>
</template>

<style scoped>
.manual-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
}

.manual-row .pill {
  justify-self: start;
}

/* .admin-form 是纵向 flex，不收住会被拉成整行宽 */
.add-row {
  align-self: flex-start;
}

.warn {
  color: var(--counter-danger);
}
</style>

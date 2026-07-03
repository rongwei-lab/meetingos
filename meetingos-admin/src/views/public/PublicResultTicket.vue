<template>
  <section class="result-ticket" :class="`result-ticket-${kind}`">
    <div class="result-ticket-hero">
      <n-icon class="result-ticket-icon" :component="CheckmarkCircleOutline" />
      <div class="result-ticket-title">查询成功</div>
      <div v-if="attendeeName" class="result-ticket-greeting">{{ attendeeName }}，{{ kind === "seat" ? "欢迎您" : "您好" }}</div>
    </div>

    <div class="result-ticket-body">
      <div class="result-ticket-number">
        <template v-if="primaryRows.length === 1">
          <span>{{ primaryRows[0].label }}</span>
          <strong>{{ primaryRows[0].value }}</strong>
        </template>
        <div v-else class="result-ticket-number-grid">
          <div v-for="row in primaryRows" :key="row.field" class="result-ticket-number-item">
            <span>{{ row.label }}</span>
            <strong>{{ row.value }}</strong>
          </div>
        </div>
      </div>

      <dl class="result-ticket-details">
        <template v-for="row in detailRows" :key="row.field">
          <dt>{{ row.label }}</dt>
          <dd>{{ row.value }}</dd>
        </template>
      </dl>

      <div v-if="noticeText" class="result-ticket-notice">
        {{ noticeText }}
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { NIcon } from "naive-ui";
import { CheckmarkCircleOutline } from "@vicons/ionicons5";
import type { QueryDisplayRow, QueryResultKind } from "./queryDisplay";

const props = defineProps<{
  kind: QueryResultKind;
  attendeeName?: string;
  rows: QueryDisplayRow[];
  notice?: string;
}>();

const fallbackPrimaryField = computed(() => (props.kind === "seat" ? "SEAT_CODE" : props.kind === "room" ? "ROOM_NO" : "DINNER_TABLE_NO"));
const fallbackPrimaryLabel = computed(() => (props.kind === "seat" ? "您的座位号" : props.kind === "room" ? "您的房间号" : "您的晚宴桌号"));

const primaryRows = computed<QueryDisplayRow[]>(() => {
  const highlightedRows = props.rows.filter((row) => row.highlighted);
  if (highlightedRows.length) return highlightedRows;

  const fallbackRow = props.rows.find((row) => row.field === fallbackPrimaryField.value);
  if (fallbackRow) {
    /*
     * 兼容未配置重点字段的旧会议：顶部仍保留原来的“您的座位号/房间号/晚宴桌号”文案。
     * 一旦后台配置了重点字段，就完全按配置字段展示，不再强行固定主字段。
     */
    return [{ ...fallbackRow, label: fallbackPrimaryLabel.value, highlighted: true }];
  }
  return [{ field: fallbackPrimaryField.value, label: fallbackPrimaryLabel.value, value: "-", highlighted: true }];
});

const noticeText = computed(() => {
  const configuredNotice = props.notice?.trim();
  if (configuredNotice) return configuredNotice;
  if (props.kind === "seat") {
    return "请按会议通知时间到达会场，凭会议证件从指定入口进入，对号入座。";
  }
  if (props.kind === "dinner") {
    return "请按现场指引前往晚宴区域入座，如有调整请以会务组通知为准。";
  }
  const tips = props.rows.find((row) => row.field === "CHECKIN_TIPS")?.value;
  return tips || "凭会议证件及身份证办理入住，接待台位于酒店大堂。";
});

const detailRows = computed(() => {
  const hiddenFields = new Set([...primaryRows.value.map((row) => row.field), "CHECKIN_TIPS"]);
  return props.rows.filter((row) => !hiddenFields.has(row.field));
});
</script>

<style scoped>
.result-ticket {
  width: min(400px, 100%);
  margin: 20px auto 0;
  overflow: hidden;
  border-radius: 28px;
  background: #ffffff;
  box-shadow: 0 28px 70px rgba(46, 54, 103, 0.16);
}

.result-ticket-hero {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 28px 28px 34px;
  color: #ffffff;
  text-align: center;
  background: linear-gradient(135deg, #5f63f1 0%, #4c39df 100%);
}

.result-ticket-room .result-ticket-hero {
  background: linear-gradient(135deg, #ad4ff0 0%, #7128e9 100%);
}

.result-ticket-dinner .result-ticket-hero {
  background: linear-gradient(135deg, #0f9f8f 0%, #0b6f83 100%);
}

.result-ticket-icon {
  font-size: 68px;
  line-height: 1;
}

.result-ticket-title {
  margin-top: 8px;
  font-size: 26px;
  font-weight: 800;
  line-height: 1.15;
}

.result-ticket-greeting {
  font-size: 18px;
  font-weight: 700;
  line-height: 1.45;
}

.result-ticket-body {
  padding: 28px 24px 26px;
}

.result-ticket-number {
  display: grid;
  justify-items: center;
  gap: 12px;
  margin: 0 0 26px;
  padding: 24px 16px 30px;
  border-radius: 18px;
  background: #eeeeff;
  text-align: center;
}

.result-ticket-room .result-ticket-number {
  background: #f7efff;
}

.result-ticket-dinner .result-ticket-number {
  background: #e9fbf7;
}

.result-ticket-number span {
  color: #8a8cf5;
  font-size: 19px;
  font-weight: 800;
  line-height: 1.3;
}

.result-ticket-room .result-ticket-number span {
  color: #ba70ff;
}

.result-ticket-dinner .result-ticket-number span {
  color: #138d80;
}

.result-ticket-number strong {
  max-width: 100%;
  color: #4535db;
  font-size: clamp(42px, 11.5vw, 58px);
  font-weight: 900;
  line-height: 1;
  overflow-wrap: anywhere;
}

.result-ticket-room .result-ticket-number strong {
  color: #8600e8;
}

.result-ticket-dinner .result-ticket-number strong {
  color: #086e72;
}

.result-ticket-number-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 22px;
  width: 100%;
}

.result-ticket-number-item {
  display: grid;
  justify-items: center;
  gap: 10px;
  min-width: 0;
}

.result-ticket-number-grid strong {
  font-size: clamp(30px, 8vw, 46px);
  line-height: 1.08;
}

.result-ticket-details {
  display: grid;
  grid-template-columns: minmax(96px, 0.8fr) minmax(0, 1.2fr);
  column-gap: 18px;
  margin: 0;
  border-top: 1px solid #edf0f6;
}

.result-ticket-details dt,
.result-ticket-details dd {
  min-width: 0;
  margin: 0;
  padding: 18px 0;
  border-bottom: 1px solid #edf0f6;
}

.result-ticket-details dt {
  color: #90a1bb;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.35;
}

.result-ticket-details dd {
  color: #2f3c50;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.4;
  text-align: right;
  overflow-wrap: anywhere;
}

.result-ticket-notice {
  margin-top: 24px;
  padding: 18px;
  border: 1px solid #ffe49c;
  border-radius: 16px;
  background: #fff9e7;
  color: #c55100;
  font-size: 18px;
  font-weight: 800;
  line-height: 1.65;
}

.result-ticket-room .result-ticket-notice {
  border-color: #ead8ff;
  background: #fbf5ff;
  color: #8000e8;
}

.result-ticket-dinner .result-ticket-notice {
  border-color: #b8eee5;
  background: #effdfa;
  color: #08756e;
}

@media (max-width: 720px) {
  .result-ticket-hero {
    padding: 24px 22px 30px;
  }

  .result-ticket-icon {
    font-size: 62px;
  }

  .result-ticket-title {
    font-size: 25px;
  }

  .result-ticket-greeting {
    font-size: 17px;
  }

  .result-ticket-body {
    padding: 26px 24px 24px;
  }

  .result-ticket-number {
    margin-bottom: 22px;
  }

  .result-ticket-number strong {
    font-size: clamp(38px, 12.5vw, 54px);
  }

  .result-ticket-details dt,
  .result-ticket-details dd {
    padding: 17px 0;
    font-size: 17px;
  }

  .result-ticket-notice {
    font-size: 17px;
  }
}
</style>

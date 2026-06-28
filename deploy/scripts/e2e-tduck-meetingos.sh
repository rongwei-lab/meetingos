#!/bin/sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
ENV_FILE="${1:-}"

if [ -z "$ENV_FILE" ]; then
  if [ -f "$ROOT_DIR/.env" ]; then
    ENV_FILE="$ROOT_DIR/.env"
  else
    ENV_FILE="$ROOT_DIR/.env.example"
  fi
fi

case "$ENV_FILE" in
  /*) ;;
  *) ENV_FILE="$PWD/$ENV_FILE" ;;
esac

if [ ! -f "$ENV_FILE" ]; then
  echo "Env file not found: $ENV_FILE" >&2
  exit 1
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

read_env() {
  awk -F= -v key="$1" '
    $1 == key {
      sub(/^[^=]*=/, "")
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", $0)
      gsub(/^"|"$/, "", $0)
      print $0
      found=1
      exit
    }
    END {
      if (!found) {
        print ""
      }
    }
  ' "$ENV_FILE"
}

json_post() {
  url=$1
  body=$2
  shift 2
  curl -sS -X POST "$url" -H "Content-Type: application/json" "$@" -d "$body"
}

expect_tduck_ok() {
  file=$1
  step=$2
  code=$(jq -r ".code // empty" "$file")
  if [ "$code" != "200" ]; then
    echo "$step failed: $(jq -c '{code,msg,data}' "$file")" >&2
    exit 1
  fi
}

expect_meetingos_ok() {
  file=$1
  step=$2
  success=$(jq -r ".success // empty" "$file")
  if [ "$success" != "true" ]; then
    echo "$step failed: $(jq -c '{success,code,message,data}' "$file")" >&2
    exit 1
  fi
}

require_cmd curl
require_cmd jq

BASE_URL=${MEETINGOS_BASE_URL:-http://127.0.0.1}
TDUCK_API_BASE_URL=${TDUCK_API_BASE_URL:-"$BASE_URL/tduck-api"}
TDUCK_FORM_PUBLIC_BASE_URL=${TDUCK_FORM_PUBLIC_BASE_URL:-"$BASE_URL/tduck"}

TDUCK_WEBHOOK_TOKEN=${TDUCK_WEBHOOK_TOKEN:-$(read_env TDUCK_WEBHOOK_TOKEN)}
if [ -z "$TDUCK_WEBHOOK_TOKEN" ]; then
  echo "TDUCK_WEBHOOK_TOKEN is required in env file or environment." >&2
  exit 1
fi
MEETINGOS_ADMIN_USERNAME=${MEETINGOS_ADMIN_USERNAME:-$(read_env MEETINGOS_INIT_ADMIN_USERNAME)}
MEETINGOS_ADMIN_PASSWORD=${MEETINGOS_ADMIN_PASSWORD:-$(read_env MEETINGOS_INIT_ADMIN_PASSWORD)}
TDUCK_ADMIN_EMAIL=${TDUCK_ADMIN_EMAIL:-$(read_env TDUCK_DEFAULT_ADMIN_EMAIL)}
TDUCK_ADMIN_PASSWORD=${TDUCK_ADMIN_PASSWORD:-$(read_env TDUCK_DEFAULT_ADMIN_PASSWORD)}

# .env.example 里故意使用 CHANGE_ME 占位值；本地演示环境仍可能使用初始化默认密码。
# 这里只作为联调兜底，生产环境请显式通过环境变量传入真实管理员密码。
[ -n "$MEETINGOS_ADMIN_USERNAME" ] || MEETINGOS_ADMIN_USERNAME=admin
case "$MEETINGOS_ADMIN_PASSWORD" in
  ""|CHANGE_ME*) MEETINGOS_ADMIN_PASSWORD=admin123 ;;
esac
[ -n "$TDUCK_ADMIN_EMAIL" ] || TDUCK_ADMIN_EMAIL=admin@tduckcloud.com
case "$TDUCK_ADMIN_PASSWORD" in
  ""|CHANGE_ME*) TDUCK_ADMIN_PASSWORD=123456 ;;
esac

MEETING_ID=${MEETING_ID:-1}
MEETING_CODE=${MEETING_CODE:-NDEF2026}
RUN_ID=$(date +%Y%m%d%H%M%S)
PHONE=${ATTENDEE_PHONE:-"139$(date +%H%M%S%S)"}
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

TDUCK_LOGIN_BODY=$(jq -n --arg account "$TDUCK_ADMIN_EMAIL" --arg password "$TDUCK_ADMIN_PASSWORD" \
  '{account:$account,password:$password}')
json_post "$TDUCK_API_BASE_URL/login/account" "$TDUCK_LOGIN_BODY" > "$TMP_DIR/tduck-login.json"
expect_tduck_ok "$TMP_DIR/tduck-login.json" "TDuck login"
TDUCK_TOKEN=$(jq -r ".data.token // empty" "$TMP_DIR/tduck-login.json")
if [ -z "$TDUCK_TOKEN" ]; then
  echo "TDuck login did not return token." >&2
  exit 1
fi

FORM_NAME="MeetingOS TDuck 联调表单 $RUN_ID"
CREATE_FORM_BODY=$(jq -n --arg name "$FORM_NAME" \
  '{name:$name,description:"MeetingOS 自动联调创建",type:1,folderId:0}')
json_post "$TDUCK_API_BASE_URL/user/form/create" "$CREATE_FORM_BODY" -H "token: $TDUCK_TOKEN" > "$TMP_DIR/create-form.json"
expect_tduck_ok "$TMP_DIR/create-form.json" "Create TDuck form"
FORM_KEY=$(jq -r ".data.formKey // empty" "$TMP_DIR/create-form.json")
if [ -z "$FORM_KEY" ]; then
  echo "Create TDuck form did not return formKey." >&2
  exit 1
fi

FIELDS_BODY=$(jq -n --arg formKey "$FORM_KEY" --arg runId "$RUN_ID" '
  def inputField($id; $label; $placeholder; $required; $index):
    {
      formKey:$formKey,
      formItemId:$id,
      type:"INPUT",
      label:$label,
      required:$required,
      showLabel:true,
      displayType:false,
      hideType:false,
      span:24,
      placeholder:$placeholder,
      regList:[],
      # TDuck 后端允许 scheme 为空，但前端渲染器依赖 config.span/formId/label 等字段。
      # 这里按 TDuck 官方初始化 SQL 的 INPUT 组件结构生成，兼容部署在 /tduck/ 子路径下的真实填写页。
      scheme:{
        style:{width:"100%"},
        config:{
          tag:"t-input",
          span:24,
          label:$label,
          append:"",
          formId:$id,
          prepend:"",
          regList:[],
          tagIcon:"input",
          dataType:{type:"",message:""},
          required:$required,
          changeTag:true,
          renderKey:($id + "-" + $runId + "-" + ($index|tostring)),
          showLabel:true
        },
        "__config__":{
          tag:"t-input",
          span:24,
          label:$label,
          append:"",
          formId:$id,
          prepend:"",
          regList:[],
          tagIcon:"input",
          dataType:{type:"",message:""},
          required:$required,
          changeTag:true,
          renderKey:($id + "-" + $runId + "-" + ($index|tostring)),
          showLabel:true
        },
        typeId:"INPUT",
        vModel:$id,
        "__vModel__":("field" + $id),
        disabled:false,
        readonly:false,
        clearable:true,
        placeholder:$placeholder,
        "prefix-icon":"",
        "suffix-icon":"",
        "show-word-limit":false
      }
    };
  [
    inputField("name"; "姓名"; "请输入姓名"; true; 1),
    inputField("phone"; "手机号"; "请输入手机号"; true; 2),
    inputField("organization"; "单位"; "请输入单位"; false; 3),
    inputField("title"; "职务"; "请输入职务"; false; 4),
    inputField("attendeeType"; "参会类型"; "GUEST / STAFF / VIP"; false; 5)
  ]')
json_post "$TDUCK_API_BASE_URL/user/form/item/batch/create" "$FIELDS_BODY" -H "token: $TDUCK_TOKEN" > "$TMP_DIR/create-fields.json"
expect_tduck_ok "$TMP_DIR/create-fields.json" "Create TDuck fields"

PUBLISH_BODY=$(jq -n --arg formKey "$FORM_KEY" '{formKey:$formKey}')
json_post "$TDUCK_API_BASE_URL/user/form/publish" "$PUBLISH_BODY" -H "token: $TDUCK_TOKEN" > "$TMP_DIR/publish.json"
expect_tduck_ok "$TMP_DIR/publish.json" "Publish TDuck form"

MO_LOGIN_BODY=$(jq -n --arg username "$MEETINGOS_ADMIN_USERNAME" --arg password "$MEETINGOS_ADMIN_PASSWORD" \
  '{username:$username,password:$password}')
json_post "$BASE_URL/api/auth/login" "$MO_LOGIN_BODY" > "$TMP_DIR/meetingos-login.json"
expect_meetingos_ok "$TMP_DIR/meetingos-login.json" "MeetingOS login"
MEETINGOS_TOKEN=$(jq -r ".data.accessToken // empty" "$TMP_DIR/meetingos-login.json")
if [ -z "$MEETINGOS_TOKEN" ]; then
  echo "MeetingOS login did not return accessToken." >&2
  exit 1
fi

REGISTRATION_URL="$TDUCK_FORM_PUBLIC_BASE_URL/s/$FORM_KEY"
BIND_BODY=$(jq -n \
  --argjson meetingId "$MEETING_ID" \
  --arg formKey "$FORM_KEY" \
  --arg formName "$FORM_NAME" \
  --arg registrationUrl "$REGISTRATION_URL" \
  '{meetingId:$meetingId,formKey:$formKey,formName:$formName,registrationUrl:$registrationUrl,bindingStatus:"BOUND",webhookStatus:"CONFIGURED"}')
json_post "$BASE_URL/api/admin/tduck/bindings" "$BIND_BODY" -H "Authorization: Bearer $MEETINGOS_TOKEN" > "$TMP_DIR/bind.json"
expect_meetingos_ok "$TMP_DIR/bind.json" "Bind MeetingOS TDuck form"

# Webhook 入库依赖字段映射。这里使用 MeetingOS 的自动同步能力做低风险首轮匹配：
# 脚本创建的 TDuck 字段 label/key 与 MeetingOS 约定一致，应该能自动匹配姓名和手机号。
curl -sS -X POST "$BASE_URL/api/admin/tduck/mappings/$MEETING_ID/sync" \
  -H "Authorization: Bearer $MEETINGOS_TOKEN" > "$TMP_DIR/sync-fields.json"
expect_meetingos_ok "$TMP_DIR/sync-fields.json" "Sync MeetingOS TDuck field mappings"
MAPPING_STATUS=$(jq -r ".data.mappingStatus // empty" "$TMP_DIR/sync-fields.json")
if [ "$MAPPING_STATUS" != "VALID" ]; then
  echo "Field mapping sync did not become VALID: $(jq -c '.data' "$TMP_DIR/sync-fields.json")" >&2
  exit 1
fi

SUBMIT_BODY=$(jq -n --arg formKey "$FORM_KEY" --arg phone "$PHONE" '{
  formKey:$formKey,
  completeTime:12,
  submitOs:"macOS",
  submitBrowser:"Chrome",
  submitUa:{os:{name:"macOS"},browser:{name:"Chrome"}},
  wxOpenId:"",
  wxUserInfo:null,
  originalData:{
    name:"真实TDuck联调",
    phone:$phone,
    organization:"TDuck联调单位",
    title:"测试嘉宾",
    attendeeType:"GUEST"
  }
}')
json_post "$TDUCK_API_BASE_URL/user/form/data/public/create" "$SUBMIT_BODY" > "$TMP_DIR/submit.json"
expect_tduck_ok "$TMP_DIR/submit.json" "Submit TDuck public form"
TDUCK_DATA_ID=$(jq -r ".data.id // empty" "$TMP_DIR/submit.json")
if [ -z "$TDUCK_DATA_ID" ]; then
  echo "TDuck submit did not return data.id." >&2
  exit 1
fi

FOUND=false
for _ in $(seq 1 30); do
  sleep 1
  curl -sS "$BASE_URL/api/admin/attendees?meetingId=$MEETING_ID&keyword=$PHONE&page=0&size=20" \
    -H "Authorization: Bearer $MEETINGOS_TOKEN" > "$TMP_DIR/attendees.json"
  if jq -e --arg dataId "$TDUCK_DATA_ID" '.data.content[]? | select(.tduckDataId == $dataId)' "$TMP_DIR/attendees.json" >/dev/null; then
    FOUND=true
    break
  fi
done

curl -sS "$BASE_URL/api/admin/tduck/sync-logs?meetingId=$MEETING_ID&page=0&size=100" \
  -H "Authorization: Bearer $MEETINGOS_TOKEN" > "$TMP_DIR/sync-logs.json"
SYNC_LOG=$(jq -c --arg dataId "$TDUCK_DATA_ID" '.data.content[]? | select(.tduckDataId == $dataId)' "$TMP_DIR/sync-logs.json")

if [ "$FOUND" != "true" ] || [ -z "$SYNC_LOG" ]; then
  echo "Webhook was not observed in MeetingOS within 30 seconds." >&2
  echo "formKey=$FORM_KEY dataId=$TDUCK_DATA_ID phone=$PHONE" >&2
  echo "Recent sync logs: $(jq -c '.data.content[0:5]' "$TMP_DIR/sync-logs.json")" >&2
  exit 1
fi

ATTENDEE=$(jq -c --arg dataId "$TDUCK_DATA_ID" '.data.content[] | select(.tduckDataId == $dataId) | {id,name,phone,organization,title,attendeeType,reviewStatus,tduckDataId}' "$TMP_DIR/attendees.json")
PUBLIC_INFO=$(curl -sS "$BASE_URL/api/public/meetings/$MEETING_CODE/registration")

echo "TDuck login: OK"
echo "TDuck form created/published: $FORM_KEY"
echo "TDuck webhook configured by MeetingOS binding: OK"
echo "TDuck public submit dataId: $TDUCK_DATA_ID"
echo "MeetingOS binding: OK"
echo "MeetingOS field mappings: $(jq -c '{status:.data.mappingStatus,fieldCount:.data.fieldCount,mappedCount:.data.mappedCount}' "$TMP_DIR/sync-fields.json")"
echo "MeetingOS attendee: $ATTENDEE"
echo "MeetingOS sync log: $(printf '%s' "$SYNC_LOG" | jq -c '{id,eventType,status,formKey,tduckDataId,attendeeId,message}')"
echo "Public registration: $(printf '%s' "$PUBLIC_INFO" | jq -c '{success:.success,formKey:.data.formKey,registrationUrl:.data.registrationUrl}')"

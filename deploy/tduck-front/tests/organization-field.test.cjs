const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { patchOrganizationSource } = require('../patch-organization-field');
const { createOrganizationInputMixin } = require('../organization-input');

const bundlePath = path.resolve(__dirname, '../../../vendor/tduck-front/node_modules/tduck-form-generator/dist/TduckForm.common.js');
const nextTurn = () => new Promise(resolve => setImmediate(resolve));
const response = data => Promise.resolve({ ok: true, json: async () => ({ success: true, data }) });
const validate = (schema, values) => new Promise((resolve, reject) => schema.validate(values,
  errors => errors ? reject(new Error(errors.map(error => error.message).join('; '))) : resolve()));

function field(fetch, overrides = {}) {
  const mixin = createOrganizationInputMixin({ fetch });
  const state = Object.assign(mixin.data(), {
    item: { config: { dataType: { type: 'organization' }, meetingOsTianyanchaEnabled: true } },
    changeValue: '北京百度', $refs: {}, $route: { params: { key: 'bound-form' } }
  }, overrides);
  for (const [name, method] of Object.entries(mixin.methods)) state[name] = method.bind(state);
  Object.defineProperty(state, 'meetingOsOrganizationLookupEnabled', {
    get: () => mixin.computed.meetingOsOrganizationLookupEnabled.call(state)
  });
  return { state, mixin };
}

test('真实 TDuck 生成器补丁可重复应用，单位格式映射到受支持的字符串校验', {
  skip: !fs.existsSync(bundlePath) && '需准备 vendor/tduck-front 依赖'
}, async () => {
  const original = fs.readFileSync(bundlePath, 'utf8');
  const patched = patchOrganizationSource(original);
  new vm.Script(patched);
  assert.equal(patchOrganizationSource(patched), patched);

  const configStart = patched.indexOf('var inputvue_type_script_lang_js = (');
  const configEnd = patched.indexOf('// CONCATENATED MODULE:', configStart);
  const config = vm.runInNewContext(patched.slice(configStart, configEnd) + '; inputvue_type_script_lang_js', { IconsDialog: {} });
  assert.equal(config.data().dataTypeList.find(item => item.value === 'organization').label, '单位名称');
  const designer = { activeData: { config: { dataType: { type: 'organization' } } }, $set: (object, key, value) => { object[key] = value; }, ...config.data() };
  config.methods.dataTypeChange.call(designer, 'organization');
  assert.equal(designer.activeData.config.meetingOsTianyanchaEnabled, false);
  designer.activeData.config.meetingOsTianyanchaEnabled = true;
  const saved = JSON.parse(JSON.stringify(designer.activeData));
  assert.equal(saved.config.meetingOsTianyanchaEnabled, true);
  config.methods.dataTypeChange.call(designer, 'email');
  assert.equal(designer.activeData.config.meetingOsTianyanchaEnabled, false);

  const method = patched.match(/handleInputValidate: (function handleInputValidate[\s\S]*?\n    })\n  }\n}\);/)[1];
  const buildRules = vm.runInThisContext('(' + method + ')');
  const SchemaModule = require(path.resolve(path.dirname(bundlePath), '../../async-validator'));
  const Schema = SchemaModule.default || SchemaModule;
  const validation = { dataType: { type: 'organization', message: '请输入单位名称' }, regList: [] };
  buildRules({ typeId: 'INPUT', config: validation }, validation);
  const schema = new Schema({ name: validation.regList });
  await validate(schema, { name: '某大学' });
  await validate(schema, { name: '' });
  await assert.rejects(validate(schema, { name: '   ' }));
  const phone = { dataType: { type: 'phone', message: '手机号错误' }, regList: [] };
  buildRules({ typeId: 'INPUT', config: phone }, phone);
  await validate(new Schema({ phone: phone.regList }), { phone: '13800000001' });
  await assert.rejects(validate(new Schema({ phone: phone.regList }), { phone: '123' }));
});

test('关闭联想、其他格式、短关键词、只读或禁用字段均不发请求', async () => {
  let calls = 0;
  const { state } = field(() => { calls++; throw Error('不得请求'); });
  for (const value of ['北', '北京', '']) {
    state.changeValue = value;
    await state.fetchMeetingOsOrganizationSuggestions(value, result => assert.deepEqual(result, []));
  }
  state.changeValue = '北京百度';
  for (const attribute of ['readonly', 'disabled']) {
    state.item[attribute] = true;
    await state.fetchMeetingOsOrganizationSuggestions(state.changeValue, result => assert.deepEqual(result, []));
    state.item[attribute] = false;
  }
  state.item.config.meetingOsTianyanchaEnabled = false;
  await state.fetchMeetingOsOrganizationSuggestions(state.changeValue, result => assert.deepEqual(result, []));
  state.item.config.meetingOsTianyanchaEnabled = true;
  state.item.config.dataType.type = 'phone';
  await state.fetchMeetingOsOrganizationSuggestions(state.changeValue, result => assert.deepEqual(result, []));
  assert.equal(calls, 0);
});

test('查询使用短期令牌，返回候选但不改输入值和其他字段', async () => {
  const requests = [];
  const entry = { id: '1', name: '北京百度网讯科技有限公司', province: '北京', status: '存续' };
  const { state } = field((url, options) => {
    requests.push({ url, options });
    return response(options.method === 'GET' ? { enabled: true, token: 'scoped-token', expiresInSeconds: 600 }
      : { available: true, code: 'OK', items: [entry] });
  });
  let suggestions;
  await state.fetchMeetingOsOrganizationSuggestions(state.changeValue, result => { suggestions = result; });
  assert.deepEqual(suggestions, [entry]);
  assert.equal(state.changeValue, '北京百度');
  assert.equal(requests.length, 2);
  assert.deepEqual(JSON.parse(requests[1].options.body), { formKey: 'bound-form', token: 'scoped-token', word: '北京百度' });
  assert.equal(requests[1].options.headers.Authorization, undefined);
});

test('无 Token 和上游失败保留手工输入，无结果时不显示提示文字', async () => {
  for (const result of [null, { available: false, code: 'UNAVAILABLE', items: [] }, { available: true, code: 'OK', items: [], message: '未找到相关单位，可继续输入或手工填写' }]) {
    const { state } = field((url, options) => response(options.method === 'GET'
      ? { enabled: true, token: 'token', expiresInSeconds: 600 } : result));
    await state.fetchMeetingOsOrganizationSuggestions(state.changeValue, items => assert.deepEqual(items, []));
    assert.equal(state.changeValue, '北京百度');
    if (result && result.available === true) assert.equal(state.meetingOsOrganizationMessage, '');
    else assert.match(state.meetingOsOrganizationMessage, /手工填写/);
  }
  const { state } = field(() => response({ enabled: false }));
  await state.fetchMeetingOsOrganizationSuggestions(state.changeValue, items => assert.deepEqual(items, []));
  assert.match(state.meetingOsOrganizationMessage, /手工填写/);
  const failure = field(() => Promise.reject(new Error('network error'))).state;
  await failure.fetchMeetingOsOrganizationSuggestions(failure.changeValue, items => assert.deepEqual(items, []));
  assert.equal(failure.changeValue, '北京百度');
});

test('晚返回的旧查询不会覆盖新查询，清空和销毁后也不会出现旧候选', async () => {
  const pending = [];
  const callbacks = [];
  const { state, mixin } = field((url, options) => options.method === 'GET'
    ? response({ enabled: true, token: 'token', expiresInSeconds: 600 })
    : new Promise(resolve => pending.push(data => resolve({ ok: true, json: async () => ({ success: true, data }) }))));
  const old = state.fetchMeetingOsOrganizationSuggestions(state.changeValue, items => callbacks.push(['old', items]));
  await nextTurn();
  state.changeValue = '北京大学';
  mixin.watch.changeValue.call(state);
  const latest = state.fetchMeetingOsOrganizationSuggestions(state.changeValue, items => callbacks.push(['latest', items]));
  await nextTurn();
  pending[1]({ available: true, items: [{ name: '北京大学' }] });
  await latest;
  pending[0]({ available: true, items: [{ name: '旧名称' }] });
  await old;
  assert.deepEqual(callbacks, [['latest', [{ name: '北京大学' }]]]);

  for (const action of ['clear', 'destroy']) {
    state.changeValue = '北京大学';
    const query = state.fetchMeetingOsOrganizationSuggestions(state.changeValue, () => callbacks.push(action));
    await nextTurn();
    if (action === 'clear') state.changeValue = '';
    else mixin.beforeDestroy.call(state);
    pending[pending.length - 1]({ available: true, items: [{ name: '旧名称' }] });
    await query;
  }
  assert.equal(callbacks.length, 1);
});

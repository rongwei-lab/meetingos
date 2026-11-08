const fs = require('fs');
const { createOrganizationInputMixin } = require('./organization-input');

function replaceOnce(source, before, after, label) {
  if (!source.includes(before) || source.indexOf(before) !== source.lastIndexOf(before)) {
    throw new Error('TDuck organization field patch shape changed: ' + label);
  }
  return source.replace(before, after);
}

function patchOrganizationSource(source) {
  if (source.includes('var meetingOsOrganizationInputMixin = ')) return source;

  source = replaceOnce(source, "        value: 'phone',\n        label: '手机号'",
    "        value: 'organization',\n        label: '单位名称'\n      }, {\n        value: 'phone',\n        label: '手机号'", 'format option');
  source = replaceOnce(source, '    dataTypeChange: function dataTypeChange(value) {', `    dataTypeChange: function dataTypeChange(value) {
      if (value === 'organization' || Object.prototype.hasOwnProperty.call(this.activeData.config, 'meetingOsTianyanchaEnabled')) {
        this.$set(this.activeData.config, 'meetingOsTianyanchaEnabled', value === 'organization' && this.activeData.config.meetingOsTianyanchaEnabled === true);
      }`, 'format change');

  const switchMarker = "  }), 1)], 1), _vm.activeData.config.dataType.type ? _c('el-form-item', {";
  source = replaceOnce(source, switchMarker, `  }), 1)], 1), _vm.activeData.config.dataType.type === 'organization' ? _c('el-form-item', {
    attrs: { "label": "启用天眼查单位联想", "label-width": "154px" }
  }, [_c('el-switch', {
    attrs: { "aria-label": "启用天眼查单位联想" },
    model: {
      value: _vm.activeData.config.meetingOsTianyanchaEnabled === true,
      callback: function(value) { _vm.$set(_vm.activeData.config, 'meetingOsTianyanchaEnabled', value); },
      expression: "activeData.config.meetingOsTianyanchaEnabled"
    }
  }), _c('el-tooltip', {
    attrs: { "content": "输入关键词显示单位候选，支持手工填写；查询失败不影响提交。", "placement": "top" }
  }, [_c('i', { staticClass: 'el-icon-question', staticStyle: { "margin-left": "8px" } })])], 1) : _vm._e(), _vm.activeData.config.dataType.type ? _c('el-form-item', {`, 'lookup switch');

  // organization 是字段语义，需转换成校验器支持的 string，不能将未知类型交给 async-validator。
  source = replaceOnce(source, "        } else if (rule.type) {\n          config.regList.push(rule);", `        } else if (rule.type === 'organization') {
          config.regList.push({ type: 'string', whitespace: true, message: rule.message || '请输入单位名称' });
        } else if (rule.type) {
          config.regList.push(rule);`, 'organization validation');

  const renderMarker = 'var TInputvue_type_template_id_41110aa8_scoped_true_render = function render() {';
  source = replaceOnce(source, renderMarker, renderMarker + `
  if (this.meetingOsOrganizationLookupEnabled) return this.renderMeetingOsOrganizationInput();`, 'input render');
  const componentMarker = `var TInputvue_type_script_lang_js = ({
  name: 'TInput',
  mixins: [mixin["a" /* default */]],`;
  source = replaceOnce(source, componentMarker, `var meetingOsOrganizationInputMixin = (${createOrganizationInputMixin.toString()})();
var TInputvue_type_script_lang_js = ({
  name: 'TInput',
  mixins: [mixin["a" /* default */], meetingOsOrganizationInputMixin],`, 'input behavior');
  return source;
}

function patchOrganizationField() {
  const file = 'node_modules/tduck-form-generator/dist/TduckForm.common.js';
  const source = patchOrganizationSource(fs.readFileSync(file, 'utf8'));
  for (const marker of ['启用天眼查单位联想', "value: 'organization'", "rule.type === 'organization'", 'renderMeetingOsOrganizationInput']) {
    if (!source.includes(marker)) throw new Error('TDuck organization field patch self-check failed: ' + marker);
  }
  fs.writeFileSync(file, source);
}

module.exports = { patchOrganizationSource, patchOrganizationField };

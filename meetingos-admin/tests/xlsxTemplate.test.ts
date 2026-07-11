import { describe, expect, it } from "vitest";
import { createXlsxTemplateBlob } from "../src/utils/xlsxTemplate";

describe("XLSX 导入模板", () => {
  it("第一工作表只保留表头，示例进入独立工作表", async () => {
    const blob = createXlsxTemplateBlob(["姓名", "手机号", "证件号"], [], {
      instructions: ["请填写真实数据"],
      exampleRows: [["张三", "13800000000", "110101199001010000"]],
      textColumnCount: 3
    });
    const entries = await readStoredZip(blob);

    expect(entries.get("xl/worksheets/sheet1.xml")).toContain("姓名");
    expect(entries.get("xl/worksheets/sheet1.xml")).not.toContain("张三");
    expect(entries.get("xl/worksheets/sheet2.xml")).toContain("张三");
    expect(entries.get("xl/worksheets/sheet3.xml")).toContain("请填写真实数据");
    expect(entries.get("xl/workbook.xml")).toContain('name="填写示例"');
    expect(entries.get("xl/workbook.xml")).toContain('name="导入说明"');
  });

  it("数据列和现有数据单元格使用文本格式", async () => {
    const blob = createXlsxTemplateBlob(["手机号", "证件号"], [["0013800000000", "110101199001010000"]], {
      textColumnCount: 2
    });
    const entries = await readStoredZip(blob);
    const worksheet = entries.get("xl/worksheets/sheet1.xml") || "";
    const styles = entries.get("xl/styles.xml") || "";

    expect(worksheet).toContain('<col min="1" max="1"');
    expect(worksheet).toContain('style="1"');
    expect(worksheet).toContain('<c r="A2" s="1" t="inlineStr"');
    expect(worksheet).toContain("0013800000000");
    expect(styles).toContain('formatCode="@"');
  });
});

async function readStoredZip(blob: Blob) {
  const bytes = new Uint8Array(await blob.arrayBuffer());
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  const decoder = new TextDecoder();
  const entries = new Map<string, string>();
  let offset = 0;

  while (offset + 30 <= bytes.length && view.getUint32(offset, true) === 0x04034b50) {
    const compressedSize = view.getUint32(offset + 18, true);
    const nameLength = view.getUint16(offset + 26, true);
    const extraLength = view.getUint16(offset + 28, true);
    const nameStart = offset + 30;
    const dataStart = nameStart + nameLength + extraLength;
    const name = decoder.decode(bytes.subarray(nameStart, nameStart + nameLength));
    entries.set(name, decoder.decode(bytes.subarray(dataStart, dataStart + compressedSize)));
    offset = dataStart + compressedSize;
  }
  return entries;
}

# Tamil check (Ravi)

`tamil_check.csv` lists every text in the app: the key, the English text and the Tamil now in the app (849 rows, none missing).

1. Open it in Excel or Google Sheets.
2. Read the Tamil column. Where it is wrong or unnatural, write the correct Tamil in the last column. Leave the last column empty when the Tamil is fine.
3. Keep `%1$s`, `%2$s` and `%1$d` exactly as they are: the app puts numbers and names there.
4. Save as CSV (UTF-8) and give it back. Claude Code then copies your corrections into `values-ta/strings.xml`.

The other 7 languages (Hindi, Bengali, Chinese, Spanish, French, German, Vietnamese) were machine-written and still need a native reader.

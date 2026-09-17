-- 套餐商品化第二批：套餐详情富文本。
-- 存的是已按白名单净化过的 HTML（见 HtmlSanitizer），可空。
ALTER TABLE plan
    ADD COLUMN detail MEDIUMTEXT NULL COMMENT '套餐详情富文本，入库前已按白名单净化的 HTML，可空' AFTER image_url;

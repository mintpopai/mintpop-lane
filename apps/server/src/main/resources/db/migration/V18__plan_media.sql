-- 套餐商品化第一批：给套餐补上面向用户的短描述与配图。
-- 两列都可空，存量套餐无需补数据，控制台按有无渲染。
ALTER TABLE plan
    ADD COLUMN description VARCHAR(255) NULL COMMENT '套餐短描述，控制台购买卡片的副标题，可空' AFTER currency,
    ADD COLUMN image_url   VARCHAR(512) NULL COMMENT '套餐图公开 URL（R2 自定义域下的地址），可空' AFTER description;

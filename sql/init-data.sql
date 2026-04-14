-- =====================================================================
-- SocialFlow - initial seed data
-- Preset Prompt templates for each platform.
-- Run AFTER schema.sql.
-- =====================================================================
USE socialflow;

-- Small Red Book (Xiaohongshu) seed template
INSERT INTO prompt_template (
  id, template_name, platform, category, system_prompt, user_prompt_template,
  variables, few_shot_examples, output_format, is_system, user_id, sort_order
) VALUES (
  1001, '小红书-种草文案', 'XIAOHONGSHU', 'SEED',
  '你是专业的小红书种草达人。擅长用亲切真诚的语气分享好物，标题吸睛，正文结构清晰，恰当使用 Emoji，读起来像真人分享。',
  '请根据以下信息生成一篇小红书种草文案：\n主题：{{topic}}\n关键词：{{keywords}}\n产品信息：{{productInfo}}\n期望字数：{{wordCount}}\n请输出标题和正文，标题不超过 20 字，正文 300-800 字，适当使用 Emoji。',
  JSON_ARRAY(
    JSON_OBJECT('name','topic','required',true,'default',''),
    JSON_OBJECT('name','keywords','required',false,'default',''),
    JSON_OBJECT('name','productInfo','required',false,'default',''),
    JSON_OBJECT('name','wordCount','required',false,'default','500')
  ),
  NULL, 'TEXT', 1, NULL, 1
);

-- Douyin script seed template
INSERT INTO prompt_template (
  id, template_name, platform, category, system_prompt, user_prompt_template,
  variables, few_shot_examples, output_format, is_system, user_id, sort_order
) VALUES (
  1002, '抖音-短视频脚本', 'DOUYIN', 'STORY',
  '你是抖音爆款视频脚本写手。口语化、强钩子、节奏快、前 3 秒必须抓住观众。',
  '请根据以下信息生成一份抖音短视频口播脚本：\n主题：{{topic}}\n核心卖点：{{keywords}}\n总时长约 60 秒，控制在 300 字以内。请在脚本开头写出 3 秒 Hook。',
  JSON_ARRAY(
    JSON_OBJECT('name','topic','required',true,'default',''),
    JSON_OBJECT('name','keywords','required',false,'default','')
  ),
  NULL, 'TEXT', 1, NULL, 2
);

-- WeChat Moments seed template
INSERT INTO prompt_template (
  id, template_name, platform, category, system_prompt, user_prompt_template,
  variables, few_shot_examples, output_format, is_system, user_id, sort_order
) VALUES (
  1003, '朋友圈-生活化文案', 'WECHAT_MOMENT', 'GENERAL',
  '你是朋友圈文案写手。要像真人发朋友圈一样生活化、有温度，避免广告感。',
  '请为以下主题写一条朋友圈文案：\n主题：{{topic}}\n字数控制在 200 字以内，少用或不用 Emoji。',
  JSON_ARRAY(JSON_OBJECT('name','topic','required',true,'default','')),
  NULL, 'TEXT', 1, NULL, 3
);

-- WeChat Official Account long-form template
INSERT INTO prompt_template (
  id, template_name, platform, category, system_prompt, user_prompt_template,
  variables, few_shot_examples, output_format, is_system, user_id, sort_order
) VALUES (
  1004, '公众号-深度长文', 'WECHAT_MP', 'TUTORIAL',
  '你是微信公众号深度长文作者。结构清晰、观点专业、有小标题、读起来有干货感。',
  '请根据以下主题撰写一篇公众号深度文章：\n主题：{{topic}}\n关键词：{{keywords}}\n参考资料：{{context}}\n字数 1000-3000 字，包含小标题、分段清晰。',
  JSON_ARRAY(
    JSON_OBJECT('name','topic','required',true,'default',''),
    JSON_OBJECT('name','keywords','required',false,'default',''),
    JSON_OBJECT('name','context','required',false,'default','')
  ),
  NULL, 'MARKDOWN', 1, NULL, 4
);

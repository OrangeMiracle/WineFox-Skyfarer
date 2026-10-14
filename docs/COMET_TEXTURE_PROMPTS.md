# 彗星之心贴图生成记录

使用 imagegen 技能及内置生成工具，各自输入对应参考图，要求透明背景。生成图随后通过 `tools/export-comet-textures.ps1` 仅做最近邻尺寸导出，最终资源为 32×32 RGBA。

## 彗星之心

参考：`tools/素材参考/彗星之心.png`

输出：`src/main/resources/assets/winefox_skyfarer/textures/item/comet_heart.png`

完整提示词：

> Convert the provided reference into an actual Minecraft item texture for Comet Heart. Preserve this exact silver featherlike crystalline comet fragment shape pointing from lower left to upper right, dark charcoal edge, white silver faceted metal and thin dormant red inner fissure. Simplify to crisp pixel art on a logical 32x32 grid (large uniform square pixels), limited palette, no antialiasing, no text, no background, no drop shadow, no extra objects, centered with 2 logical pixels padding. Genuine transparent RGBA background. Output a square PNG texture, same design rather than redesign.

## 永燃彗星之心

参考：`tools/素材参考/永燃彗星之心.png`

输出：`src/main/resources/assets/winefox_skyfarer/textures/item/everburning_comet_heart.png`

完整提示词：

> Create an actual Minecraft item texture faithful to this supplied Everburning Comet Heart reference. Same diagonal lower-left to upper-right silver crystalline feather/shard, segmented sharp silver plates surrounding brilliant red molten star core with tiny cream-white center, charcoal edge. Preserve silhouette and color identity, no redesign. Simplify to a logical 32x32 square pixel grid suitable at inventory scale, crisp square pixels, limited palette, no antialiasing, no text, no extra objects. True transparent RGBA background with two logical pixels margin. Square PNG, no backdrop, no cast shadow.

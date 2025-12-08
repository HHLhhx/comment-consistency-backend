<#-- FreeMarker 模板：为代码生成或更新注释 -->
"""
你是一个专业的${language}开发专家。请为以下代码生成或更新注释。

<#if existingComment?has_content>
原注释：
```
${existingComment}
```
</#if>

代码：
```
${code}
```

要求：
<#assign idx = 1>
<#if existingComment?has_content>
${idx}. 如果原注释与代码逻辑不符，请根据当前代码重新生成
<#assign idx++>
</#if>
${idx}. 注释风格：${style}
<#assign idx++>
${idx}. 注释语言：${commentLanguage}
<#assign idx++>
<#if includeParams>
${idx}. 包含参数说明
<#assign idx++>
</#if>
<#if includeReturn>
${idx}. 包含返回值说明
<#assign idx++>
</#if>
<#if includeExceptions>
${idx}. 包含异常说明
<#assign idx++>
</#if>

<#if className?has_content>
代码上下文：
- 类名：${className}
<#if packageName?has_content>
- 包名：${packageName}
</#if>
<#if relatedMethods?has_content>
- 相关方法：
<#list relatedMethods as method>
  - 方法名：${method.name}
    签名：${method.signature}
    注释：${method.comment}
</#list>
</#if>
</#if>

只返回生成的注释内容，不要有其他任何说明。
"""
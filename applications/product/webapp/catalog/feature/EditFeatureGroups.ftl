<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<@section>
    <@table type="data-list" autoAltRows=true>
        <@thead>
          <@tr class="header-row">
            <@th>${uiLabelMap.CommonId}</@th>
            <@th>${uiLabelMap.CommonDescription}</@th>
            <@th>&nbsp;</@th>
            <@th>&nbsp;</@th>
          </@tr>
        </@thead>
        <@tbody>
          <#list productFeatureGroups as productFeatureGroup>
            <@tr valign="middle">
                <@td><a href="<@pageUrl>EditFeatureGroupAppls?productFeatureGroupId=${productFeatureGroup.productFeatureGroupId}</@pageUrl>" class="${styles.link_nav_info_id!}">${productFeatureGroup.productFeatureGroupId}</a></@td>
                <@td>
                    <form method="post" action="<@pageUrl>UpdateProductFeatureGroup</@pageUrl>">
                    <input type="hidden" name="productFeatureGroupId" value="${productFeatureGroup.productFeatureGroupId}" />
                    <input type="text" size="30" name="description" value="${productFeatureGroup.description!}" />
                    <input type="submit" value="${uiLabelMap.CommonUpdate}" class="${styles.link_run_sys!} ${styles.action_update!}" />
                    </form>
                </@td>
                <@td><a href="<@pageUrl>EditFeatureGroupAppls?productFeatureGroupId=${productFeatureGroup.productFeatureGroupId}</@pageUrl>" class="${styles.link_nav!}">${uiLabelMap.ProductFeatureGroupAppls}</a></@td>
            </@tr>
          </#list>
        </@tbody>
    </@table>
</@section>

<@section title=uiLabelMap.ProductCreateProductFeatureGroup>
        <form method="post" action="<@pageUrl>CreateProductFeatureGroup</@pageUrl>">
            <@field type="input" label=uiLabelMap.CommonDescription size="30" name="description" value="" />
            <@field type="submit" text=uiLabelMap.CommonCreate class="+${styles.link_run_sys!} ${styles.action_add!}" />
        </form>
</@section>
<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<@script>
    $("#dialog").dialog('open');
    $(function() {
        $( "#internalOrg" ).dialog({ autoOpen: true, width: 350});
    });
</@script>
<div id="internalOrg" title="Add Internal Organization">
    <@render resource="component://humanres/widget/EmplPositionScreens.xml#EditInternalOrgOnlyForm" />
</div>
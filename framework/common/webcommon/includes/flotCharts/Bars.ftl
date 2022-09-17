<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->
<@script src=makeContentUrl("/images/jquery/plugins/flot/excanvas.min.js") />
<@script src=makeContentUrl("/images/jquery/plugins/flot/jquery.flot.js") />

<div id="${chartId}Div" style="width:600px;height:300px;"></div>

<@script>
  jQuery(document).ready( function() {
     /* Code Example: How should a chart Data Object look like */
    /*var d1 = [[0, Math.ceil(Math.random()*40)]];
    var d2 = [[1, Math.ceil(Math.random()*30)]];
    var d3 = [[2, Math.ceil(Math.random()*20)]];
    var d4 = [[3, Math.ceil(Math.random()*10)]];
    var d5 = [[4, Math.ceil(Math.random()*10)]];
    var data = [
        {data:d1, label: 'Comedy'},
        {data:d2, label: 'Action'},
        {data:d3, label: 'Romance'},
        {data:d4, label: 'Drama'},
        {data:d5, label: 'Other'}
    ];*/
    /* End Example */

    var labelsAsText = "${escapeVal(labelsText, 'js')}";
    var labels = [];
    labels = labelsAsText.split(",");

    var dataAsText = "${escapeVal(dataText, 'js')}";
    var chartData = [];
    chartData = dataAsText.split(',');

    var allData = [];
    var y = 0;
    for(var i=0; i<chartData.length-1 ; i=i+2) {
        var coordinates = [chartData[i], chartData[i+1]];
        allData[y] = {label: labels[y], data: [coordinates]};
        y++;
    }

    var options = {
        series: {
                 bars: {show: true, barWidth: 0.9,steps: 2, align: 'center',}
                 },
         grid: { hoverable: true, autoHighlight: true },
    };

    jQuery.plot(jQuery("#${chartId}Div"), allData, options);

    // function to show the mouse hover tooltip effect
    var previousPoint = null;
    jQuery("#${chartId}Div").bind("plothover", function (event, pos, item) {
    if (item) {
        if (previousPoint != item.datapoint) {
            previousPoint = item.datapoint;

            jQuery("#tooltip").remove();
            var x = item.datapoint[0],
                y = item.datapoint[1] - item.datapoint[2];

            showTooltip(item.pageX, item.pageY, x + "/" + y + " " + item.series.label);
        }
        }
        else {
            jQuery("#tooltip").remove();
            previousPoint = null;
        }
    });

    // TODO make a nice looking tooltip
    function showTooltip(x, y, contents) {
      var tooltip = jQuery('div#tooltip').length > 0 ? jQuery('div#tooltip').html(contents) : jQuery('<div id="tooltip" calss="tooltip">' + contents + '</div>').appendTo("body");
      var padding = 10,
      left = x + padding,
      top = y + padding;

      tooltip.css( {
          position: 'absolute',
          top: top,
          left: left,
          'z-index': 100000,
            opacity: 0.80
      }).show();
    }

  });
</@script>

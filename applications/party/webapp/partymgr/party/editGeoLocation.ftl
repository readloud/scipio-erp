<#--
This file is subject to the terms and conditions defined in the
files 'LICENSE' and 'NOTICE', which are part of this source
code package.
-->

<@script src="https://maps.googleapis.com/maps/api/js?sensor=false" />
<@script>
    function load() {
        var geocoder = new google.maps.Geocoder();
        var center = new google.maps.LatLng(${latitude!38}, ${longitude!15});
        var map = new google.maps.Map(document.getElementById("map"),
          { center: center,
            zoom: 15, // 0=World, 19=max zoom in
            mapTypeId: google.maps.MapTypeId.ROADMAP
          });

        var marker = new google.maps.Marker({
          position: center,
          map: map,
          draggable: true
        });
    
        document.getElementById("lat").value = center.lat().toFixed(5);
        document.getElementById("lng").value = center.lng().toFixed(5);

        google.maps.event.addListener(marker, "dragend", function() {
          var point = marker.getPosition();
          map.panTo(point);
          document.getElementById("lat").value = point.lat().toFixed(5);
          document.getElementById("lng").value = point.lng().toFixed(5);
        });
    
    
        google.maps.event.addListener(map, "moveend", function() {
            map.clearOverlays();
            var center = map.getCenter();
            var marker = new GMarker(center, {draggable: true});
            map.addOverlay(marker);
            document.getElementById("lat").value = center.lat().toFixed(5);
            document.getElementById("lng").value = center.lng().toFixed(5);
        });
    
        google.maps.event.addListener(marker, "dragend", function() {
            var point = marker.getPoint();
            map.panTo(point);
            document.getElementById("lat").value = point.lat().toFixed(5);
            document.getElementById("lng").value = point.lng().toFixed(5);
        });
    }

    function showAddress(address) {
        var map = new google.maps.Map(document.getElementById("map"),
          { center: new google.maps.LatLng(${latitude!38}, ${longitude!15}),
            zoom: 15, // 0=World, 19=max zoom in
            mapTypeId: google.maps.MapTypeId.ROADMAP
          });
        var geocoder = new google.maps.Geocoder();
        if (geocoder) {
            geocoder.geocode({'address': address}, function(result, status) {
              if (status != google.maps.GeocoderStatus.OK) {
                showErrorAlert("${escapeVal(uiLabelMap.CommonErrorMessage2, 'js')}","${escapeVal(uiLabelMap.CommonAddressNotFound, 'js')}");
            } else {
                var point = result[0].geometry.location; 
                var lat = point.lat().toFixed(5);
                var lng = point.lng().toFixed(5);
                document.getElementById("lat").value = lat; 
                document.getElementById("lng").value = lng;
                //map.clearOverlays()
                map.setCenter(point, 14);
        
                var marker = new google.maps.Marker({
                  position: new google.maps.LatLng(lat, lng),
                  map: map,
                  draggable: true
                });
                
                google.maps.event.addListener(marker, "dragend", function() {
                  var point = marker.getPosition();
                  map.panTo(point);
                  document.getElementById("lat").value = point.lat().toFixed(5);
                  document.getElementById("lng").value = point.lng().toFixed(5);
                });

                google.maps.event.addListener(map, "moveend", function() {
                    //map.clearOverlays();
                    var center = map.getCenter();
                    var marker = new google.maps.Marker(center, {draggable: true});
                    map.addOverlay(marker);
                    document.getElementById("lat").value = center.lat().toFixed(5);
                    document.getElementById("lng").value = center.lng().toFixed(5);
                });

                google.maps.event.addListener(marker, "dragend", function() {
                    var pt = marker.getPoint();
                    map.panTo(pt);
                    document.getElementById("lat").value = pt.lat().toFixed(5);
                    document.getElementById("lng").value = pt.lng().toFixed(5);
                });
            }
        });
        }
    }
</@script>

<body onload="load()">
    <center>
        <div align="center" id="map" style="border:1px solid #979797; background-color:#e5e3df; width:500px; height:450px; margin:2em auto;"><br/></div>
        <form action="#" onsubmit="showAddress(this.address.value); return false">
            <input type="text" size="50" name="address"/>
            <input type="submit" value="${uiLabelMap.CommonSearch}" class="${styles.link_run_sys!} ${styles.action_find!}"/>
        </form>
        <br/><br/>
        <form id="updateMapForm" method="post" action="<@pageUrl>editGeoLocation</@pageUrl>">
            <input type="hidden" name="partyId" value="${partyId!}"/>
            <input type="hidden" name="geoPointId" value="${geoPointId!}"/>
            <input type="hidden" name="lat" id="lat"/>
            <input type="hidden" name="lng" id="lng"/>
            <input type="submit" id="createMapButton" class="${styles.link_run_sys!} ${styles.action_add!}" value="${uiLabelMap.CommonSubmit}"/>
        </form>
        <br/><br/><br/>
    </center>
</body>

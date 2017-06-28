########################## Websocket handlers ##########################

$ ->
  ws = new WebSocket $("body").data("ws-url")
  ws.onmessage = (event) ->
    message = JSON.parse event.data
    switch message.type
      when "stockhistory"
        populateStockHistory(message)
      when "stockupdate"
        updateStockChart(message)
      when "twitterUserStats"
        console.log(message)
        updateUserData(message.userData)
        addTwitterUserStats(message)
      when "locationStats"
        console.log(message)
        updateLocationData(message)
        addLocationStats(message)
      when "twitterUserStatsUpdate"
#        addTwitterUserStats(message)
      else
        console.error("Unknown message type: " + message.type)

  $("#addsymbolform").submit (event) ->
    event.preventDefault()
    # send the message to watch the stock
    ws.send(JSON.stringify({type: "stock", symbol: $("#addsymboltext").val()}))
    # reset the form
    $("#addsymboltext").val("")

  #  $("#twitter-user-form").submit (event) ->
  #    console.log("$(#twitter-user-form).submit (event) ->")
  #    event.preventDefault()
  #    # send the message to watch the stock
  #    ws.send(JSON.stringify({type: "twitterUser", userName: $("#twitter-user-text").val()}))
  #    # reset the form
  #    $('#postModal').modal('toggle')
  #    $("#twitter-user-text").val("")

  ws.onopen = (event) ->
    if (getQueryVariable("type") == "location")
      ws.send(JSON.stringify({
        type: "location",
        locationLat: getQueryVariable("locationLat"),
        locationLng: getQueryVariable("locationLng")
      }))
    else if (getQueryVariable("type") == "twitterUsername")
      ws.send(JSON.stringify({type: "twitterUser", userName: getQueryVariable("content")}))
    else
      console.error("Unknow type=" + getQueryVariable("type"))

getQueryVariable = (variable) ->
  query = window.location.search.substring(1)
  vars = query.split("&")
  i = 0

  while i < vars.length
    pair = vars[i].split("=")
    return pair[1]  if pair[0] is variable
    i++
  false

########################## Stock plot ##########################

getPricesFromArray = (data) ->
  (v[1] for v in data)

getChartArray = (data) ->
  ([i, v] for v, i in data)

#getChartOptions = (data) ->
#  series:
#    shadowSize: 0
#  yaxis:
#    min: getAxisMin(data)
#    max: getAxisMax(data)
#  xaxis:
#    show: true

getChartOptions = (data) ->
  series:
    bars:
      show: true
      barWidth: 0.6
      align: "center"
  yaxis:
    min: getAxisMin(data)
    max: getAxisMax(data)
  xaxis:
    show: true
    mode: "categories"
    tickLength: 0

getAxisMin = (data) ->
  Math.min.apply(Math, data) * 0.9

getAxisMax = (data) ->
  Math.max.apply(Math, data) * 1.1

populateStockHistory = (message) ->
  chart = $("<div>").addClass("chart").prop("id", message.symbol)
  chartHolder = $("<div>").addClass("chart-holder").append(chart)
  chartHolder.append($("<p>").text("values are simulated"))
  detailsHolder = $("<div>").addClass("details-holder")
  flipper = $("<div>").addClass("flipper").append(chartHolder).append(detailsHolder).attr("data-content", message.symbol)
  flipContainer = $("<div>").addClass("flip-container").append(flipper).click (event) ->
    handleFlip($(this))
  $("#stocks").prepend(flipContainer)
  plot = chart.plot([getChartArray(message.history)], getChartOptions(message.history)).data("plot")

  # Update plot on window resize for responsive design
  $(window).resize (event) ->
    chart.plot([getChartArray(message.history)], getChartOptions(message.history)).data("plot")

updateStockChart = (message) ->
  if ($("#" + message.symbol).size() > 0)
    plot = $("#" + message.symbol).data("plot")
    data = getPricesFromArray(plot.getData()[0].data)
    data.shift()
    data.push(message.price)
    plot.setData([getChartArray(data)])
    # update the yaxes if either the min or max is now out of the acceptable range
    yaxes = plot.getOptions().yaxes[0]
    if ((getAxisMin(data) < yaxes.min) || (getAxisMax(data) > yaxes.max))
# reseting yaxes
      yaxes.min = getAxisMin(data)
      yaxes.max = getAxisMax(data)
      plot.setupGrid()
    # redraw the chart
    plot.draw()

########################## Twitter user plot ##########################
getUserChartOptions = (data) ->
  series:
    bars:
      show: true
      barWidth: 0.6
      align: "center"
  xaxis:
    mode: "categories"
    tickLength: 0

updateUserData = (userData) ->
  name = $("<p>").addClass("lead").html(userData.name)
  console.log("userData.followers_count: with toString() " + userData.followers_count)
  basicStats = $("<p>").html(
    abbreviateNumber(userData.followers_count).bold() + " Followers, " +
      abbreviateNumber(userData.statuses_count).bold() + " Posts")
  $("#user-basic-data").empty()
  $("#user-basic-data").append(name)
  $("#user-basic-data").append(basicStats)
  $("#profile-image").attr("src", userData.profile_image_url);

addTwitterUserStats = (message) ->
  if ($("#" + message.username).size() > 0)
    plot = $("#" + message.username)
    plot.plot([message.topHashtags], getUserChartOptions(message.topHashtags)).data("plot")
  else
    chart = $("<div>").addClass("chart").prop("id", message.username)
    chartHolder = $("<div>").addClass("chart-holder").append(chart)
    chartHolder.append($("<p>").text("values are simulated"))
    detailsHolder = $("<div>").addClass("details-holder")
    flipper = $("<div>").addClass("flipper").append(chartHolder).append(detailsHolder).attr("data-content", message.username).prop("id", "flipper" + message.username)
    flipContainer = $("<div>").addClass("flip-container").append(flipper).click (event) ->
      handleFlip($(this))
    $("#flipper" + message.username).remove()
    $("#stocks").prepend(flipContainer)

    plot = chart.plot([message.topHashtags], getUserChartOptions(message.topHashtags)).data("plot");

    # Update plot on window resize for responsive design
    $(window).resize (event) ->
      chart.plot([message.topHashtags], getUserChartOptions(message.topHashtags)).data("plot");

########################## Location stats ##########################
getLocationChartOptions = (data) ->
  series:
    bars:
      show: true
      barWidth: 0.6
      align: "center"
  xaxis:
    mode: "categories"
    tickLength: 0

updateLocationData = (message) ->
  name = $("<p>").addClass("lead").html(message.name)
  #  console.log("message.followers_count: with toString() " + message.followers_count)
  #  basicStats = $("<p>").html(
  #    abbreviateNumber(message.followers_count).bold() + " Followers, " +
  #      abbreviateNumber(message.statuses_count).bold() + " Posts")
  $("#user-basic-data").empty()
  $("#user-basic-data").append(name)
#  $("#user-basic-data").append(basicStats)
#  $("#profile-image").attr("src",message.profile_image_url);

addLocationStats = (message) ->

  panelHeading =
    """
    <div class="panel-heading">
        <a href="#" class="pull-right">View all</a>
        <h4>Trending topics in """ + message.name + """</h4>
    </div>
    """

  trendsList = $("<div>").addClass("list-group")
  for trend, i in message.locationTrends
    listItem = $("<a>", href: trend.url, target: "blank")
      .addClass("list-group-item")
      .append(i+1 + ". " + trend.name)
      .append("""<span class="check-twitter">Check out on Twitter</span>""")
    trendsList.append(listItem)
  panelBody = $("<div>").addClass("panel-body")
  panelBody.append(trendsList)

  panel = $("<div>").addClass("panel panel-default")
  panel.append(panelHeading)
  panel.append(panelBody)
  $("#stocks").prepend(panel)

#  if ($("#" + message.username).size() > 0)
#    plot = $("#" + message.username)
#    plot.plot([message.topHashtags], getLocationChartOptions(message.topHashtags)).data("plot")
#  else
#    chart = $("<div>").addClass("chart").prop("id", message.username)
#    chartHolder = $("<div>").addClass("chart-holder").append(chart)
#    chartHolder.append($("<p>").text("values are simulated"))
#    detailsHolder = $("<div>").addClass("details-holder")
#    flipper = $("<div>").addClass("flipper").append(chartHolder).append(detailsHolder).attr("data-content", message.username).prop("id", "flipper" + message.username)
#    flipContainer = $("<div>").addClass("flip-container").append(flipper).click (event) ->
#      handleFlip($(this))
#    $("#flipper" + message.username).remove()
#    $("#stocks").prepend(flipContainer)
#
#    plot = chart.plot([message.topHashtags], getLocationChartOptions(message.topHashtags)).data("plot");
#
#    # Update plot on window resize for responsive design
#    $(window).resize (event) ->
#      chart.plot([message.topHashtags], getLocationChartOptions(message.topHashtags)).data("plot");

########################## Common ##########################

abbreviateNumber = (number) ->
  SI_POSTFIXES = ["", "k", "M", "G", "T", "P", "E"]
  tier = Math.log10(Math.abs(number)) / 3 | 0
  if(tier == 0)
    number.toString()
  else
    postfix = SI_POSTFIXES[tier]
    scale = Math.pow(10, tier * 3)
    scaled = number / scale
    formatted = scaled.toFixed(1) + ''
    if (/\.0$/.test(formatted))
      formatted = formatted.substr(0, formatted.length - 2)
    formatted + postfix

handleFlip = (container) ->
  if (container.hasClass("flipped"))
    container.removeClass("flipped")
    container.find(".details-holder").empty()
  else
    container.addClass("flipped")
    # fetch stock details and tweet
    $.ajax
      url: "/sentiment/" + container.children(".flipper").attr("data-content")
      dataType: "json"
      context: container
      success: (data) ->
        detailsHolder = $(this).find(".details-holder")
        detailsHolder.empty()
        switch data.label
          when "pos"
            detailsHolder.append($("<h4>").text("The tweets say BUY!"))
            detailsHolder.append($("<img>").attr("src", "/assets/images/buy.png"))
          when "neg"
            detailsHolder.append($("<h4>").text("The tweets say SELL!"))
            detailsHolder.append($("<img>").attr("src", "/assets/images/sell.png"))
          else
            detailsHolder.append($("<h4>").text("The tweets say HOLD!"))
            detailsHolder.append($("<img>").attr("src", "/assets/images/hold.png"))
      error: (jqXHR, textStatus, error) ->
        detailsHolder = $(this).find(".details-holder")
        detailsHolder.empty()
        detailsHolder.append($("<h2>").text("Error: " + JSON.parse(jqXHR.responseText).error))
    # display loading info
    detailsHolder = container.find(".details-holder")
    detailsHolder.append($("<h4>").text("Determing whether you should buy or sell based on the sentiment of recent tweets..."))
    detailsHolder.append($("<div>").addClass("progress progress-striped active").append($("<div>").addClass("bar").css("width", "100%")))

########################## Google maps ##########################

#// Note: This example requires that you consent to location sharing when
#// prompted by your browser. If you see the error "The Geolocation service
#// failed.", it means you probably did not give permission for the browser to
#// locate you.
map = undefined
infoWindow = undefined
userPosition = undefined

window.initMap = ->
  map = new (google.maps.Map)(document.getElementById('map'),
    center:
      lat: -34.397
      lng: 150.644
    zoom: 6)
  infoWindow = new (google.maps.InfoWindow)
  # Try HTML5 geolocation.
  if navigator.geolocation
    navigator.geolocation.getCurrentPosition ((position) ->
      userPosition =
        lat: position.coords.latitude
        lng: position.coords.longitude
      console.log 'Pos: lat=' + userPosition.lat + ', lng=' + userPosition.lng
      $("#locationLat").val(userPosition.lat)
      $("#locationLng").val(userPosition.lng)
      infoWindow.setPosition userPosition
      infoWindow.setContent 'Location found.'
      infoWindow.open map
      map.setCenter userPosition
      return
    ), ->
      handleLocationError true, infoWindow, map.getCenter()
      return
  else
# Browser doesn't support Geolocation
    handleLocationError false, infoWindow, map.getCenter()
  return

handleLocationError = (browserHasGeolocation, infoWindow, pos) ->
  infoWindow.setPosition pos
  infoWindow.setContent if browserHasGeolocation then 'Error: The Geolocation service failed.' else 'Error: Your browser doesn\'t support geolocation.'
  infoWindow.open map
  return


# Load google map when modal is opened
$('#postModal').on 'shown.bs.modal', ->
  mapScript =
    """
    <script async defe
    src="https://maps.googleapis.com/maps/api/js?key=AIzaSyAb9dQMdg9CP9ZPB-rrkfqpyd_AjlLqh4I&callback=initMap">
    </script>
    """
  $("#postModal").prepend(mapScript)
  console.log("Modal show")
  $('#twitter-user-text').focus()
  return

$(document).ready ->
  $('div.bhoechie-tab-menu>div.list-group>a').click (e) ->
    e.preventDefault()
    $(this).siblings('a.active').removeClass 'active'
    $(this).addClass 'active'
    index = $(this).index()
    $('div.bhoechie-tab>div.bhoechie-tab-content').removeClass 'active'
    $('div.bhoechie-tab>div.bhoechie-tab-content').eq(index).addClass 'active'
    return
  return
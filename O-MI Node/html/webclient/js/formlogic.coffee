
###########################################################################
#  Copyright (c) 2015 Aalto University.
#
#  Licensed under the 4-clause BSD (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at top most directory of project.
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
##########################################################################

######################
# formLogic sub module
formLogicExt = ($, WebOmi) ->
  my = WebOmi.formLogic = {}

  # Sets xml or string to request field
  my.setRequest = (xml) ->
    mirror = WebOmi.consts.requestCodeMirror
    if not xml?
      mirror.setValue ""
    else if typeof xml == "string"
      mirror.setValue xml
    else
      mirror.setValue new XMLSerializer().serializeToString xml

    mirror.autoFormatAll()

  # Gets the current request (possibly having users manual edits) as XMLDocument
  my.getRequest = () ->
    str = WebOmi.consts.requestCodeMirror.getValue()
    WebOmi.omi.parseXml str

  # Do stuff with RequestDocument and automatically write it back
  # callback: Function () -> ()
  my.modifyRequest = (callback) ->
    req = my.getRequest() # RemoveMe
    callback()
    #my.setRequest _
    WebOmi.requests.generate()

  my.getRequestOdf = () ->
    WebOmi.error "getRequestOdf is deprecated"
    str = WebOmi.consts.requestCodeMirror.getValue()
    o.evaluateXPath(str, '//odf:Objects')[0]

  # Remove current response from its CodeMirror and hide it with animation
  my.clearResponse = ->
    mirror = WebOmi.consts.responseCodeMirror
    mirror.setValue ""
    WebOmi.consts.responseDiv.slideUp()

  # Sets response (as a string or xml) and handles slide animation
  my.setResponse = (xml) ->
    mirror = WebOmi.consts.responseCodeMirror
    if typeof xml == "string"
      mirror.setValue xml
    else
      mirror.setValue new XMLSerializer().serializeToString xml
    mirror.autoFormatAll()
    # refresh as we "resize" so more text will become visible
    WebOmi.consts.responseDiv.slideDown complete : ->
      mirror.refresh()
    mirror.refresh()
  

  ########################################################
  # SUBSCRIPTION HISTORY CODE, TODO: move to another file?
  ########################################################
  # Subscription history of WebSocket and callback=0 Features

  # List of subscriptions that uses websocket and should be watched
  # Type: {String_RequestID : {receivedCount : Number, userSeenCount : Number, listSelector : Jquery}}
  my.callbackSubscriptions = {}
  
  # Set true when the next response should be rendered to main response area
  my.waitingForResponse = false

  # Set true whene next response with requestID should be saved to my.callbackSubscriptions
  my.waitingForRequestID = false

  # whether callbackResponseHistoryModal is opened and the user can see the new results
  #my.historyOpen = false

  consts = WebOmi.consts

  consts.afterJquery ->
    consts.callbackResponseHistoryModal = $ '.callbackResponseHistory'
    consts.callbackResponseHistoryModal
      .on 'shown.bs.modal', ->
        #my.historyOpen = true
        my.updateHistoryCounter()
      .on 'hide.bs.modal', ->
        #my.historyOpen = false
        my.updateHistoryCounter()

    consts.responseListCollection  = $ '.responseListCollection'
    consts.responseListCloneTarget = $ '.responseList.cloneTarget'
    consts.historyCounter = $ 'label.historyCounter'

  # end afterjquery

  my.updateHistoryCounter = () ->
    update = (sub) ->
      sub.userSeenCount = sub.receivedCount

    ####################################
    # TODO: historyCounter
    #if my.historyOpen
    my.callbackSubscriptions =
      (update sub for sub in my.callbackSubscriptions)


  # Called when we receive relevant websocket response
  # response: String
  # returns: true if the response was consumed, false otherwise
  my.handleSubscriptionHistory = (responseString) ->
    if my.waitingForResponse and not my.waitingForRequestID
      return false
    # imports
    omi = WebOmi.omi

    response = omi.parseXml responseString

    # get requestID
    requestID = parseInt omi.evaluateXPath(response, "//omi:requestID/text()")[0] # headOption
    if (not requestID?) or (not my.callbackSubscriptions[requestID])
      if my.waitingForRequestID
        my.waitingForRequestID = false
        my.callbackSubscriptions[requestID] =
          receivedCount : 1
          userSeenCount : 0
      else
        return false

    infoitems = omi.evaluateXPath(response, "//odf:InfoItem")

    getPath = (xmlNode) ->
      id = omi.getOdfId(xmlNode)
      if id? and id != "Objects"
        init = getPath xmlNode.parentNode
        init + "/" + id
      else
        id

    getPathValues = (infoitemXmlNode) ->
      valuesXml = omi.evaluateXPath(infoitemXmlNode, "./odf:value")
      path = getPath infoitemXml
      for value in valuesXml
        path: path
        values: value

    pathValues = ( getPathValues info for info in infoitems )

    # Utility function; Clone the element above and empty its input fields 
    # callback type: (clonedDom) -> void
    cloneAbove = (target, callback) ->
      util.cloneAbove target, cloned ->
      
        cloned.slideDown null, ->  # animation, default duration
          # readjusts the position because of size change (see modal docs)
          consts.infoItemDialog.modal 'handleUpdate'

    createHistory = (requestID) ->
      newList = cloneAbove consts.responseListCloneTarget
      newList
        .removeClass "cloneTarget"
        .show()
      newList.find '.requestID'
        .text requestID
      newList

    # return: jquery elem
    returnStatus = ( count, returnCode ) ->
      row = $ "<tr>"
        .addClass switch Math.floor(returnCode/100)
          when 2 then "success" # 2xx
          when 3 then "warning" # 3xx
          when 4 then "danger"  # 4xx
        .append $ "<th>"
          .text count
        .append $ "<th>returnCode</th>"
        .append $ "<th>"
          .text returnCode

    htmlformat = ( pathValues ) ->
      pathValuePairs = (pathValue) ->
        ({path: pathValue.path, value: value} for value in pathValue.values)

      lines = pathValuePairs for pathValue in pathValues

      for pathValue in lines
        $ "<tr><td></td><td>"+pathValue.path+"</td><td>"+pathValue.value+"</td></tr>"

    addHistory = ( requestID, pathValues ) ->
      maybeCBRecord = callbackSubscriptions[requestID]
      if maybeCBRecord.selector?
        callbackRecord = maybeCBRecord
        callbackRecord.selector
          .find "dataTable"
          .prepend returnStatus callbackRecord.receivedCount, 200
              .after htmlformat pathValues
      else
        newHistory = createHistory requestID
        newHistory.add (returnStatus 1, 200)
          .after htmlformat pathValues
        callbackSubscriptions[requestID].selector = newHistory

    addHistory requestID, pathValues

  

  
  my.createWebSocket = (onopen, onclose, onmessage, onerror) -> # Should socket be created automaticly for my or 
    WebOmi.debug "Creating WebSocket."
    consts = WebOmi.consts
    server = consts.serverUrl.val()
    socket = new WebSocket(server)
    socket.onopen = () ->
      WebOmi.debug "WebSocket connected."
      WebOmi.debug "Sending request via WebSocket."
      socket.send(request)
    socket.onclose = () ->
      WebOmi.debug "WebSocket disconnected."
    socket.onmessage = (message) ->
      # TODO: Check if response to subscription and put into subscription response view
      response = message.data
      consts.progressBar.css "width", "100%"
      my.setResponse response
      consts.progressBar.css "width", "0%"
      consts.progressBar.hide()
      window.setTimeout (-> consts.progressBar.show()), 2000
      #callback(response) if (callback?)
    socket.onerror = (error) ->
      WebOmi.debug "WebSocket error: ", error
    my.socket = socket
  
  # send, callback is called with response text if successful
  my.send = (callback) ->
    consts = WebOmi.consts
    my.clearResponse()
    server  = consts.serverUrl.val()
    request = consts.requestCodeMirror.getValue()
    if server.startsWith("ws://") || server.startsWith("wss://")
      WebOmi.debug "Sending request via WebSocket."
      my.wsSend request
    else
      WebOmi.debug "Sending request with HTTP POST."
      my.httpSend callback

  my.wsSend = (request) ->
    if( !my.socket || my.socket.readyState != WebSocket.OPEN)
      onopen = () ->
        WebOmi.debug "WebSocket connected."

        # Next message should be rendered to main response area
        my.waitingForResponse = true

        # Check if request is zero callback request
        omi = WebOmi.omi
        maybeParsedXml = Maybe omi.parsedXml(request)
        maybeVerbXml =
          maybeParsedXml.bind (parsedXml) ->
            verbResult = omi.evaluateXPath(parsedXml, "//omi:omiEnvelope/*")
            Maybe.fromArray verbResult

        maybeVerbXml.fmap (verbXml) ->
          verb = verbXml.tagName
          maybeCallback = verbXml.attributes.callback

          if maybeCallback.exists((c) -> c is "0")
            # commented because user might be waiting for some earlier response
            #y.waitingForResponse = false
            
            my.waitingForRequestID = true

        my.socket.send(request)
      onclose = () -> WebOmi.debug "WebSocket disconnected."
      onerror = (error) -> WebOmi.debug "WebSocket error: ",error
      onmessage = my.handleWSMessage
      my.createWebSocket onopen, onclose, onmessage, onerror
    else
      WebOmi.debug "Sending request via WebSocket."
      my.socket.send(request)

  my.httpSend = (callback) ->
    consts = WebOmi.consts
    server  = consts.serverUrl.val()
    request = consts.requestCodeMirror.getValue()
    consts.progressBar.css "width", "95%"
    $.ajax
      type: "POST"
      url: server
      data: request
      contentType: "text/xml"
      processData: false
      dataType: "text"
      #complete: -> true
      error: (response) ->
        consts.progressBar.css "width", "100%"
        my.setResponse response.responseText
        consts.progressBar.css "width", "0%"
        consts.progressBar.hide()
        window.setTimeout (-> consts.progressBar.show()), 2000
        # TODO: Tell somewhere the "Bad Request" etc
        # response.statusText
      success: (response) ->
        consts.progressBar.css "width", "100%"
        my.setResponse response
        consts.progressBar.css "width", "0%"
        consts.progressBar.hide()
        window.setTimeout (-> consts.progressBar.show()), 2000
        callback(response) if (callback?)
  
  my.handleWSMessage = (message) ->
    consts = WebOmi.consts
    # TODO: Check if response to subscription and put into subscription response view
    response = message.data
    if not my.handleSubscriptionHistory response
      consts.progressBar.css "width", "100%"
      my.setResponse response
      consts.progressBar.css "width", "0%"
      consts.progressBar.hide()
      window.setTimeout (-> consts.progressBar.show()), 2000
      my.waitingForResponse = false





  # recursively build odf jstree from the Objects xml node
  my.buildOdfTree = (objectsNode) ->
    # imports
    tree = WebOmi.consts.odfTree
    evaluateXPath = WebOmi.omi.evaluateXPath

    objChildren = (xmlNode) ->
      evaluateXPath xmlNode, './odf:InfoItem | ./odf:Object'

    # generate jstree data
    genData = (xmlNode, parentPath) ->
      switch xmlNode.nodeName
        when "Objects"
          name = xmlNode.nodeName
          id   : idesc name
          text : name
          state : {opened : true}
          type : "objects"
          children :
            genData(child, name) for child in objChildren(xmlNode)
        when "Object"
          name = WebOmi.omi.getOdfId(xmlNode) # FIXME: get
          path = "#{parentPath}/#{name}"
          id   : idesc path
          text : name
          type : "object"
          children :
            genData(child, path) for child in objChildren(xmlNode)
        when "InfoItem"
          name = WebOmi.omi.getOdfId(xmlNode) # FIXME: get
          path = "#{parentPath}/#{name}"
          id   : idesc path
          text : name
          type : "infoitem"
          children :
            [genData {nodeName:"MetaData"}, path]
        when "MetaData"
          path = "#{parentPath}/MetaData"
          id   : idesc path
          text : "MetaData"
          type : "metadata"
          children : []

    treeData = genData objectsNode
    tree.settings.core.data = [treeData]
    tree.refresh()


  # parse xml string and build odf jstree
  my.buildOdfTreeStr = (responseString) ->
    omi = WebOmi.omi

    parsed = omi.parseXml responseString # FIXME: get

    objectsArr = omi.evaluateXPath parsed, "//odf:Objects"

    if objectsArr.length != 1
      WebOmi.error "failed to get single Objects odf root"
    else
      my.buildOdfTree objectsArr[0] # head, checked above


  WebOmi # export

# extend WebOmi
window.WebOmi = formLogicExt($, window.WebOmi || {})




##########################
# Intialize widgets: connect events, import
((consts, requests, formLogic) ->
  consts.afterJquery ->

    # Buttons

    consts.readAllBtn
      .on 'click', -> requests.readAll(true)
    consts.sendBtn
      .on 'click', -> formLogic.send()

    consts.resetAllBtn
      .on 'click', ->
        requests.forceLoadParams requests.defaults.empty()
        closetime = 1500 # ms to close Objects jstree
        for child in consts.odfTree.get_children_dom 'Objects'
          consts.odfTree.close_all child, closetime
        formLogic.clearResponse()


    # TODO: maybe move these to centralized place consts.ui._.something
    # These widgets have a special functionality, others are in consts.ui._

    # Odf tree
    consts.ui.odf.ref
      .on "changed.jstree", (_, data) ->
        switch data.action
          when "select_node"
            odfTreePath = data.node.id
            formLogic.modifyRequest -> requests.params.odf.add odfTreePath
          when "deselect_node"
            odfTreePath = data.node.id
            formLogic.modifyRequest -> requests.params.odf.remove odfTreePath
            $ jqesc odfTreePath
              .children ".jstree-children"
              .find ".jstree-node"
              .each (_, node) ->
                consts.odfTree.deselect_node node, true


    # Request select tree
    consts.ui.request.ref
      .on "select_node.jstree", (_, data) ->
        # TODO: should ^ this ^ be changed "changed.jstree" event because it can be prevented easily
        # if data.action != "select_node" then return

        reqName = data.node.id
        WebOmi.debug reqName

        # force selection to readOnce
        if reqName == "readReq"
          consts.ui.request.set "read" # should trigger a new event
        else
          # update ui enabled/disabled settings (can have <msg>, interval, newest, oldest, timeframe?)
          ui = WebOmi.consts.ui

          readReqWidgets = [ui.newest, ui.oldest, ui.begin, ui.end]
          isReadReq = switch reqName
            when "readAll", "read", "readReq" then true
            else false
          isRequestIdReq = switch reqName
            when"cancel", "poll" then true
            else false

          for input in readReqWidgets
            input.ref.prop('disabled', not isReadReq)
            input.set null
            input.ref.trigger "input"

          # TODO: better way of removing the disabled settings from the request xml
          ui.requestID.ref.prop('disabled', not isRequestIdReq)
          if not isRequestIdReq
            ui.requestID.set null
            ui.requestID.ref.trigger "input"
          ui.interval.ref.prop('disabled', reqName != 'subscription')
          ui.interval.set null
          ui.interval.ref.trigger "input"

          formLogic.modifyRequest ->
            requests.params.name.update reqName
            # update msg status
            newHasMsg = requests.defaults[reqName]().msg
            requests.params.msg.update newHasMsg

    # for basic input fields

    makeRequestUpdater = (input) ->
      (val) ->
        formLogic.modifyRequest -> requests.params[input].update val

    for own inputVar, controls of consts.ui
      if controls.bindTo?
        controls.bindTo makeRequestUpdater inputVar

    null # no return



)(window.WebOmi.consts, window.WebOmi.requests, window.WebOmi.formLogic)

$ ->
  $('.optional-parameters > a')
    .on 'click', () ->
      glyph = $(this).find('span.glyphicon')
      if glyph.hasClass('glyphicon-menu-right')
        glyph.removeClass('glyphicon-menu-right')
        glyph.addClass('glyphicon-menu-down')
      else
        glyph.removeClass('glyphicon-menu-down')
        glyph.addClass('glyphicon-menu-right')


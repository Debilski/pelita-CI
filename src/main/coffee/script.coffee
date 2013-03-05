
this.updateNumWorkers = (num) ->
  console.log "Num Workers #{num}"
  $("#num-workers span").text(num)

this.updateQueueSize = (num) ->
  console.log "Queue Size #{num}"
  $("#queue-size span").text(num)

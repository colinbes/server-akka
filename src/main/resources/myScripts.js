const app = new Vue({
  el: '#app',
  data: {
    message: 'Vue/SSE Example!',
    now: 'wait for it ...',
    dataList: []
  },
  created () {
    this.setupStream()
  },
  methods: {
    setup () {
      this.now = 'yup'
    },
    setupStream () {
      let evtSource = new EventSource("http://localhost:8082/events")
                
      evtSource.addEventListener('myEvent', event => {
        let data = JSON.parse(event.data)
        this.now = data.msg
        this.dataList.push(data.msg)
      }, false)

      evtSource.addEventListener('error', event => {
          if (event.readyState == EventSource.CLOSED) {
              console.log('Event was closed');
              console.log(EventSource);
          }
      }, false);          
    }
  }
})
/*
  setTimeout(function () {
    console.log('closing event source')
    evtSource.close()
    evtSource = null
  }, 15000)
*/ 
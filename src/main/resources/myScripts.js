const app = new Vue({
  el: '#app',
  data: {
    message: 'Vue/SSE Example!',
    now: 'wait for it ...',
    username: '?',
    online: false,
    value: null
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
        if (data.event === 'date') {
            this.now = data.value
            this.value = data.value
        } else if (data.event="userchange") {
            this.username = data.value.name
            this.online = data.value.online
        }
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
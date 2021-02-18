const app = new Vue({
  el: '#app',
  data() {
      return {
        redisKey1: '',
        redisValue1: '',
        redisTimeout: 0,
        message: 'Vue/SSE Example!',
        now: 'wait for it ...',
        username: '?',
        online: false,
        value: null,
        actors: []
      }
  },
  computed: {
      formValid() {
        return true
      }
  },
  created () {
    this.setupStream()
    this.getActors()
  },
  methods: {
    setup () {
      this.now = 'yup'
    },
    getActors() {
        axios.get('http://localhost:8082/api/actors')
            .then(response => {
                const dd = response.data
                this.actors = dd
            })
    },
    setRedisKey(key, value, expires) {
        let dataSet = {key: key, value: value}

        if (expires > 0) {
            dataSet.expires = parseInt(expires)
        }
        console.log(`dataset ${dataSet}`)


        axios({
          method: 'post',
          url: 'http://localhost:8082/api/set',
          data: dataSet
        })
            .then(response => {
                console.log(`POST set key ${response.data}`)
            })
            .catch (error => console.log(error))
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
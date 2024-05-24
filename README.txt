Test Task for potential employer.

Запускать сначала ./run-server.sh, затем из другого окна ./run-client.sh [city] [day|week]
Можно из без client, а тупо из браузера: http://localhost:8080/?city=Moscow&period=week (параметры необязательные).

Я не мог не воспользоваться случаем и не пощупать WebFlux.
Разумеется, потратил прорву времени на гугление и собирание граблей, и одну граблю так и не нашёл как лечить:
после успешного выполнения запроса к серверу и вывода результатов, клиент несколько секунд не завершается;
видимо фоновые потоки WebFlux-а висят на таймауте, хз как их рубить.

Местами код несколько раздолбайский (e.g. DTO with public fields), о чём я там же в каментах и поясняю.

Все каменты в коде -- на рунглише, т.к. гитхаб всё-таки. %)

Какие данные брал:
- Из weatherapi.com     средние данные за сутки.
- Из weather.yandex.ru  средние данные данные за 12-часовой день (без ночи).
Усреднять самому -- лень. В реале я первым делом попросил бы уточнить, что именно нужно.

Город/регион оба провайдера отдают по-разному и не вполне понятные, поэтому в ответе - и копия `city` из запроса,
и что приехало от каждого провайдера. См. каменты в классах провайдеров внутри DTO.

Ответы сгруппировал по провайдеру:
- так красивше;
- если клиент предпочитает конкретного провайдера, ему будет проще.



Примеры ответов:


http://localhost:8080/
{
    "error":null,
    "city":"Moscow",
    "byProvider":{
        "WeatherYandexRu":{
            "error":null,
            "city":"Москва",
            "data":[
                {"date":"2024-05-24","temperature":22,"wind":2,"condition":"clear"}
            ]
        },
        "WeatherApiCom":{
            "error":null,
            "city":"Moscow City",
            "data":[
                {"date":"2024-05-24","temperature":14,"wind":3,"condition":"Sunny"}
            ]
        }
    }
}


http://localhost:8080/?city=Туапсе&period=week
...ну там то же самое...


$ ./run-client.sh
City: Moscow
Provider: WeatherYandexRu
    City: Москва
    Data:
        2024-05-24    22 °C    2 m/s   clear
Provider: WeatherApiCom
    City: Moscow City
    Data:
        2024-05-24    14 °C    3 m/s   Sunny


$ ./run-client.sh Туапсе week
City: Туапсе
Provider: WeatherYandexRu
    City: Туапсе
    Data:
        2024-05-24    28 °C    8 m/s   clear
        2024-05-25    22 °C    5 m/s   cloudy
        2024-05-26    20 °C    3 m/s   overcast
        2024-05-27    22 °C    3 m/s   cloudy
        2024-05-28    20 °C    3 m/s   light-rain
        2024-05-29    22 °C    3 m/s   cloudy
        2024-05-30    24 °C    2 m/s   cloudy
Provider: WeatherApiCom
    City: Krasnodar
    Data:
        2024-05-24    19 °C    7 m/s   Patchy rain nearby
        2024-05-25    17 °C    5 m/s   Patchy rain nearby
        2024-05-26    16 °C    5 m/s   Patchy rain nearby
        2024-05-27    18 °C    4 m/s   Patchy rain nearby
        2024-05-28    18 °C    4 m/s   Moderate rain
        2024-05-29    18 °C    3 m/s   Patchy rain nearby
        2024-05-30    19 °C    3 m/s   Sunny

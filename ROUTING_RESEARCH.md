# ملاحظات توجيه السيارة/التاكسي — 2026-10-13

## نتيجة المقارنة

- اختار المستخدم نمط سيارة/تاكسي لمسافة طريق منفصلة. لا يستبدل ذلك geofence المحلي في منطق الإنذار.
- OSRM يوفّر `route` لملف قيادة، ويعيد المسافة بالمتر؛ ملفات النقل تُحدد مسبقاً على السيرفر. هذه مسافة المسار المقترح، وليست قياساً لمسافة سيارة الأجرة التي اتبعت فعلياً.
- خادم `routing.openstreetmap.de` مُدار من FOSSGIS ويذكر ملفات car/bike/foot، مع تغطية سيارة عالمية. سياسته: نسبة المصدر ورابط إصلاح الخريطة، User-Agent صالح، حد أقصى طلب واحد/ثانية، لا scraping ولا استخدام كثيف. يصرّح بأن طلب المسار يُرسل إلى الخادم ويُحفظ في سجل الخادم.
- وثائق OSRM تعرض خدمة عامة للتجربة؛ لا تُعامل كـSLA لمنتج واسع النطاق. للاستعمال الكبير/التجاري، يلزم استضافة موثوقة ذات شروط مناسبة أو خادم خاص.
- OpenRouteService يفرض حدوداً؛ خططه وحساباته/مفاتيحه ليست ضمن صلاحيات هذه الجلسة. Valhalla محرك مفتوح المصدر لكن التوجيه المستضاف يحتاج مزوداً أو استضافة. OpenTripPlanner موجّه للنقل العام ويحتاج بيانات GTFS/NeTEx وOSM وخلفية server، وليس حلاً بسيطاً لسيارة/تاكسي.

## التنفيذ بعد موافقة المستخدم

وافق المستخدم في 2026-10-13 على إرسال الإحداثيات **عند تفعيل الميزة**. لذلك إعداد `roadDistanceEnabled` افتراضياً مغلق، ويعرض التطبيق إفصاحاً وموافقة إضافية داخل إعدادات التطبيق عند التفعيل. تُرسل نقطة GPS الحالية والوجهة فقط بدقة خمس خانات عشرية؛ لا اسم وجهة ولا سجل GPS أو مسار كامل. لا تُحفظ استجابة الطريق محلياً، ولا توجد API key مضمنة.

طلبات الطريق لا تُرسل إلا أثناء رحلة فعالة، وعلى شاشة الرحلة في foreground، مع اتصال إنترنت وقراءة GPS حديثة صالحة، وليست الرحلة متوقفة أو منتهية. المعدل محدود بحد أدنى 30 ثانية بين الطلبات، ومع السكون يُحدّث كل 60 ثانية. عند الفشل/الانقطاع تعرض الواجهة أن تقدير الطريق غير متاح أو أن آخر تقدير قديم، وتبقي المسافة المستقيمة التي يعتمدها geofence منفصلة. تقدير الطريق لا يحكم الإنذار ولا يتضمن المرور أو ETA.

تم اختبار endpoint مرة واحدة بإحداثيات Berlin العامة الواردة في مثال التوثيق (لا إحداثيات شخصية): استجاب `routing.openstreetmap.de` بـ`code=Ok` ومسافة `1888 m`. لا يثبت هذا استمرارية الخدمة؛ الخادم مجتمعي بلا SLA.

## Sources

- https://project-osrm.org/docs/v5.5.1/api/ — OSRM profiles and route response.
- https://github.com/Project-OSRM/osrm-backend — OSRM use/demo/self-hosting overview.
- https://routing.openstreetmap.de/about.html — FOSSGIS OSRM host, data, privacy, attribution, 1 req/s limit, no heavy use.
- https://openrouteservice.org/restrictions/ — hosted API restrictions.
- https://stadiamaps.com/pricing/ — hosted service pricing and plan info.
- https://valhalla.github.io/valhalla/api/route/api-reference/ — Valhalla modes/costing.
- https://docs.opentripplanner.org/en/dev-2.x/Product-Overview/ — transit data/backend model.
- https://docs.opentripplanner.org/en/v2.3.0/Basic-Tutorial/ — GTFS and OSM graph/server requirements.

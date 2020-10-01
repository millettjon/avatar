(ns rave.avatar.config)

;; web service port
(def port 9090)

;; image sizes and quality to convert to
(def image-sizes {:small {:px 75
                          :quality 0.6}
                  :med   {:px 150
                          :quality 0.7}
                  :large {:px 300
                          :quality 0.8}})

;; map of what each image type should be converted to
(def convert-to {:image/gif  :image/webp
                 :image/jpeg :image/jpeg
                 :image/png  :image/jpeg
                 :image/webp :image/webp})

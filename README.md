# Aplicativo Android de Monitoramento Veicular OBD-II

Este é um aplicativo Android desenvolvido em Kotlin que se conecta ao dispositivo OBD-II do veículo via Bluetooth, coleta várias métricas do veículo e publica os dados no AWS IoT usando o protocolo MQTT.

## Sumário

- [Recursos](#recursos)
- [Pré-requisitos](#pré-requisitos)
- [Instalação](#instalação)
- [Configuração](#configuração)
- [Uso](#uso)
- [Dependências](#dependências)
- [Notas Importantes](#notas-importantes)
- [Licença](#licença)

## Recursos

- Conexão com dispositivos OBD-II via Bluetooth.
- Coleta de métricas em tempo real, como RPM, velocidade, temperatura do motor, nível de combustível e mais.
- Publicação dos dados coletados no AWS IoT Core usando MQTT.
- Exibição das métricas na interface do usuário.
- Manipulação de permissões e conectividade Bluetooth.

## Pré-requisitos

- Dispositivo Android com Android 5.0 (Lollipop) ou superior.
- Capacidade Bluetooth.
- Adaptador OBD-II Bluetooth conectado ao veículo.
- Conta AWS com o AWS IoT Core configurado.
- Certificados e políticas do AWS IoT configurados.
- Conectividade com a internet no dispositivo Android.

## Instalação

**Clone o Repositório**
   
Abra no Android Studio

Abra o Android Studio.
Clique em File > Open.
Navegue até a pasta do repositório clonado e selecione-a.
Construa o Projeto

Permita que o Gradle sincronize e construa o projeto.
Resolva quaisquer dependências se solicitado.

##Configuração
Configuração do AWS IoT
Criar Certificados no AWS IoT Console

Faça login no console da AWS.
Navegue até o AWS IoT Core.
Vá em Secure > Certificates.
Clique em "Create certificate".
Baixe o certificado, a chave privada e o certificado raiz (Amazon Root CA).
Anexar Política ao Certificado

Crie uma política IoT que permita publicar no tópico MQTT desejado.
Anexe a política ao seu certificado.
Configurar Endpoint do AWS IoT

Anote seu endpoint do AWS IoT, que tem o formato abcdefghijklm.iot.region.amazonaws.com.
Configuração do Aplicativo
Coloque os Certificados no Projeto

Copie os certificados e as chaves para o diretório app/src/main/assets/:

aws-root-ca.pem
client-certificate.pem.crt
private.pem.key
Atualize as Constantes no Código

Abra MainActivity.kt.

Defina o endpoint do AWS IoT:

kotlin
Copiar código
private val awsIotEndpoint = "seu-endpoint-aws-iot"
Defina o client ID (pode ser qualquer string única):

kotlin
Copiar código
private val clientId = "seu-client-id"
Permissões

Certifique-se de que as seguintes permissões estão declaradas no AndroidManifest.xml:

xml
Copiar código
<uses-permission android:name="android.permission.BLUETOOTH"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN"/>
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.INTERNET"/>

##Uso
Execute o Aplicativo

Conecte seu dispositivo Android via USB ou use um emulador com suporte a Bluetooth.
Clique em Run no Android Studio para instalar o aplicativo no seu dispositivo.
Conecte-se ao Dispositivo OBD-II

Certifique-se de que seu adaptador OBD-II Bluetooth está conectado ao veículo e que o veículo está ligado.
Emparelhe seu dispositivo Android com o adaptador OBD-II nas configurações de Bluetooth.

##Colete Dados

Abra o aplicativo.
O aplicativo solicitará que você selecione o dispositivo OBD-II pareado.
Uma vez conectado, o aplicativo começará a exibir métricas em tempo real do veículo.
As métricas serão publicadas no AWS IoT Core via MQTT.

##Dependências
AWS IoT Device SDK for Android
Gson
Bibliotecas AndroidX
Material Components for Android

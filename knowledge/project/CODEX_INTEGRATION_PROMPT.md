# CODEX / AGENT PROMPT: Integrate Music-to-Light Pipeline into SmartRoomController

You are an expert embedded C++ software engineer working on an ESP32 DevKit V1 firmware project.
Your task is to integrate the newly created high-performance **Music-to-Light Conversion Pipeline** into the active codebase.

---

## 1. Context & Objectives

- **Target Project Directory**: `SmartRoomController_Complete_Source/SmartRoomController/`
- **Target File to Modify**: `SmartRoomController_Complete_Source/SmartRoomController/Hardware.cpp`
- **New Pipeline Files Already Present**:
  - `SmartRoomController_Complete_Source/SmartRoomController/MusicToLightPipeline.h`
  - `SmartRoomController_Complete_Source/SmartRoomController/MusicToLightPipeline.cpp`
- **DO NOT TOUCH** any other functionality: Wi-Fi, Spotify, IR remote learning/transmission, OLED display, NVS persistence, JSON database, or OTA must remain completely intact and functional.
- **Hardware Profile (Immutable)**:
  - LED Strip Data: GPIO 23 (`led_data`), 300 pixels
  - INMP441 Microphone: BCLK = GPIO 26 (`microphone_bclk`), WS = GPIO 25 (`microphone_ws`), DIN = GPIO 35 (`microphone_data`)

---

## 2. Exact Changes Required in `Hardware.cpp`

Open `SmartRoomController_Complete_Source/SmartRoomController/Hardware.cpp` and apply the following four modifications:

### Modification 1: Include Header and Declare Static Instances
Near line 14, right after `#include <new>`, include `MusicToLightPipeline.h` and declare static instances:

```cpp
#include <new>
#include "MusicToLightPipeline.h"

static MusicAudioDSP s_musicDSP;
static MusicLightingEngine s_musicLighting;
static MusicAudioFeatures s_latestAudio;
```

---

### Modification 2: Initialize Engines in `RoomApp::setupHardware()`
Inside `void RoomApp::setupHardware()`, find the LED strip initialization block (around lines 35–38):

**Find this block:**
```cpp
      strip=new(std::nothrow) Adafruit_NeoPixel(config::pixels,led_data,type);
      if(strip&&strip->getPixels()){strip->begin();strip->clear();strip->show();ledsReady=true;}
```

**Replace with:**
```cpp
      strip=new(std::nothrow) Adafruit_NeoPixel(config::pixels,led_data,type);
      if(strip&&strip->getPixels()){
        strip->begin();
        strip->clear();
        strip->show();
        ledsReady=true;

        // Initialize the music-to-light DSP and lighting engines
        s_musicDSP.begin(config::sampleRate, config::audioSamples);
        s_musicLighting.begin(config::pixels);
        s_musicLighting.setMaxPowerMilliamps(
            hw["max_milliamps"].as<float>(),
            hw["pixel_max_milliamps"].as<float>()
        );
      }
```

---

### Modification 3: Upgrade `RoomApp::lightingTick()`
Find `void RoomApp::lightingTick()` (around lines 62–91).

**Find this exact block (lines 68–90):**
```cpp
  for(auto& p:pixels)p=color;
  if(effect!="solid"){
    bool quiet=!soundActive;String policy=db["microphone"]["quiet_behavior"]|"dim";
    if(quiet&&policy=="hold")return;
    float response=0;
    if(effect=="level")response=rms;
    else if(effect=="bass")response=bands[0];
    else if(effect=="beat"){
      static float pulse=0;float decay=.75f+.23f*(1-l["speed"].as<float>()/100.f);
      pulse=beat?1:pulse*decay;response=pulse;
    }else if(effect=="bands"){
      for(unsigned i=0;i<config::pixels;++i){float band=bands[std::min(2u,i*3/config::pixels)];pixels[i]=color;pixels[i].scale(constrain(band*intensity,0.f,1.f));}
      if(quiet)for(auto& p:pixels)p=color;
      response=1;
    }
    response=quietDimScale(!quiet,policy=="off",response);
    brightness*=constrain(response*(effect=="bands"?1:1+intensity*3),0.f,1.f);
  }
  float estimated=0;for(auto p:pixels)estimated+=(p.r+p.g+p.b)/765.f;
  estimated*=db["hardware"]["pixel_max_milliamps"].as<float>();
  if(estimated*brightness>db["hardware"]["max_milliamps"].as<float>())brightness=db["hardware"]["max_milliamps"].as<float>()/estimated;
  // This is a configured estimate, not current sensing or a substitute for fusing.
  brightness=constrain(brightness,0.f,1.f);for(unsigned i=0;i<config::pixels;++i){auto p=pixels[i];strip->setPixelColor(i,uint8_t(p.r*brightness),uint8_t(p.g*brightness),uint8_t(p.b*brightness));}strip->show();beat=false;
```

**Replace with:**
```cpp
  float speed = l["speed"].as<float>() / 100.0f;

  // Render via the high-fidelity Music Lighting Engine
  s_musicLighting.render(
      s_latestAudio,
      effect.c_str(),
      value,
      brightness,
      speed,
      intensity,
      reinterpret_cast<MusicPixel*>(pixels),
      config::pixels
  );

  // Transfer rendered pixels to Adafruit_NeoPixel strip
  for (unsigned i = 0; i < config::pixels; ++i) {
    strip->setPixelColor(i, pixels[i].r, pixels[i].g, pixels[i].b);
  }
  strip->show();
  beat = false;
```

---

### Modification 4: Upgrade `RoomApp::audioTask()`
Find `void RoomApp::audioTask()` (around lines 93–129).

**Find this exact block (lines 100–127):**
```cpp
    float mean=0,sum=0;for(unsigned i=0;i<config::audioSamples;++i){real[i]=static_cast<float>(input[i])/2147483648.f;mean+=real[i];imag[i]=0;}mean/=config::audioSamples;
    for(unsigned i=0;i<config::audioSamples;++i){real[i]-=mean;sum+=real[i]*real[i];}
    float level=sqrtf(sum/config::audioSamples);
    fft.windowing(FFTWindow::Hamming,FFTDirection::Forward);fft.compute(FFTDirection::Forward);fft.complexToMagnitude();
    float energy[3]={};unsigned count[3]={};
    for(unsigned k=1;k<config::audioSamples/2;++k){float hz=k*float(config::sampleRate)/config::audioSamples;if(hz<40||hz>7500)continue;int b=hz<250?0:hz<2000?1:2;energy[b]+=real[k]*real[k];++count[b];}
    xSemaphoreTakeRecursive(mutex,portMAX_DELAY);
    lastAudioMs=millis();auto m=db["microphone"];float sensitivity=m["sensitivity"].as<float>();float gain=.5f+sensitivity*1.5f;
    if(calibrating){
      calibrationSum+=level;++calibrationCount;
      if(static_cast<int32_t>(millis()-calibrationEnd)>=0){
        float previous=db["microphone"]["noise_floor"]|.01f;
        float measured=constrain(calibrationSum/std::max(1u,calibrationCount)*1.5f,0.f,1.f);
        db["microphone"]["noise_floor"]=measured;calibrating=false;
        if(save()){noise=measured;calibrationError="";jobs.update(calibrationJob,"succeeded","Calibration persisted",millis());notify("Calibration saved");}
        else{db["microphone"]["noise_floor"]=previous;noise=previous;calibrationError="Calibration persistence failed";jobs.update(calibrationJob,"failed",calibrationError.c_str(),millis());notify(calibrationError);}
      }
    }
    if(m["automatic"].as<bool>()&&!soundActive&&!calibrating)noise=.998f*noise+.002f*level;
    if(!m["automatic"].as<bool>())noise=m["noise_floor"]|.01f;
    float smooth=m["smoothing"]|.7f;
    rms=smooth*rms+(1-smooth)*constrain((level-noise)*gain,0.f,1.f);
    for(int b=0;b<3;++b){float normalized=sqrtf(energy[b]/std::max(1u,count[b]))/config::audioSamples*gain*3;bands[b]=smooth*bands[b]+(1-smooth)*constrain(normalized,0.f,1.f);}
    static uint32_t lastBeat=0;bool detected=bands[0]>averageBass*1.5f&&rms>.03f&&millis()-lastBeat>180;
    averageBass=.97f*averageBass+.03f*bands[0];if(detected){beat=true;lastBeat=millis();}
    if(rms>.03f)lastSound=millis();bool active=millis()-lastSound<m["quiet_seconds"].as<unsigned>()*1000u&&lastSound!=0;
    if(active!=soundActive){soundActive=active;event(active?"sound_started":"sound_stopped");}
```

**Replace with:**
```cpp
    xSemaphoreTakeRecursive(mutex, portMAX_DELAY);
    lastAudioMs = millis();
    auto m = db["microphone"];
    float sensitivity = m["sensitivity"].as<float>();

    // Process through 16-band DSP, Tilt, AGC & Beat Tracking
    s_musicDSP.process(input, bytes / sizeof(int32_t), noise, sensitivity, s_latestAudio);

    // Keep existing RoomApp state variables synchronized for OLED and App telemetry
    rms = s_latestAudio.rms;
    beat = s_latestAudio.beat;
    soundActive = s_latestAudio.soundActive;
    bands[0] = s_latestAudio.bands[0];   // Bass (Sub/Kick)
    bands[1] = s_latestAudio.bands[6];   // Mid (Vocals/Snare)
    bands[2] = s_latestAudio.bands[13];  // Treble (Cymbals)

    if (soundActive) lastSound = millis();
    bool active = (millis() - lastSound < m["quiet_seconds"].as<unsigned>() * 1000u) && (lastSound != 0);
    if (active != soundActive) {
      soundActive = active;
      event(active ? "sound_started" : "sound_stopped");
    }
```

---

## 3. Verification Criteria

After making the edits, verify that:
1. `reinterpret_cast<MusicPixel*>(pixels)` compiles cleanly because `MusicPixel` has the exact memory layout (`uint8_t r, g, b`) as `RoomApp::Pixel`.
2. `config::pixels` (300) and `config::sampleRate` (16000) match the parameters passed into `s_musicLighting.begin()` and `s_musicDSP.begin()`.
3. The app telemetry (`rms`, `bands[3]`, `beat`, `soundActive`) continues to update accurately.
4. No other file in the directory is touched.

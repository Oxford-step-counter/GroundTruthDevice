#include <RFduinoBLE.h>

const int PIN1 = 6;
const int PIN2 = 5;

int min1 = 2000;
int max1 = 0;
int min2 = 2000;
int max2 = 0;

unsigned long lastThresholdsTime = 0;
const int PENALISE = 5000; // time after which min and max are penalised
const int MINDIFF = 10; // minimum difference to be kept between min and max
const int PENALFACTOR = 10; // penalise min/max of a factor of (max-min)/PENALFACTOR

// the debounce time; increase if the output flickers
unsigned long debounceDelay = 500;

// the last time the output pin was toggled
unsigned long lastStep1Time = 0;
unsigned long lastStep2Time = 0;
boolean lastStep1 = false;
boolean lastStep2 = false; 
byte state = 0;

void setup() {
  Serial.begin(9600);
  pinMode(PIN1, INPUT_PULLUP);
  pinMode(PIN2, INPUT_PULLUP);

  RFduinoBLE.advertisementData = "steps";
  RFduinoBLE.begin();
  RFduinoBLE.sendInt(state);
}

void loop() {
  int val1 = analogRead(PIN1);
  if (val1<min1) min1 = val1;
  if (val1>max1) max1 = val1;
  int thre1 = ((max1 - min1) /2) + min1;
  boolean step1Read = ! (val1 > thre1);

  int val2 = analogRead(PIN2);
  if (val2<min2) min2 = val2;
  if (val2>max2) max2 = val2;
  int thre2 = ((max2 - min2) /2) + min2;
  boolean step2Read = ! (val2 > thre2);


  //1, right
  if (step1Read != lastStep1) {
    if ((millis() - lastStep1Time) > debounceDelay) {
      lastStep1Time = millis();
      lastStep1 = step1Read;
    }
  }
  //2, left
  if (step2Read != lastStep2) {
    if ((millis() - lastStep2Time) > debounceDelay) {
      lastStep2Time = millis();
      lastStep2 = step2Read;
    }
  }

  byte newstate = 0;
  if(step1Read && !step2Read) newstate = 1;
  else if(!step1Read && step2Read) newstate = 2;
  else if(step1Read && step2Read) newstate = 3;

  if(newstate != state) {
    state = newstate;
    RFduinoBLE.sendInt(state);
  }

  if (millis() - lastThresholdsTime > PENALISE) {
    int diff = max1 - min1;
    if (diff > MINDIFF) {
      int penalty = diff/PENALFACTOR;
      min1 = min1 + penalty;
      max1 = max1 - penalty;
    }
    diff = max2 - min2;
    if (diff > MINDIFF) {
      int penalty = diff/PENALFACTOR;
      min2 = min2 + penalty;
      max2 = max2 - penalty;
    }
    lastThresholdsTime = millis();
  }

  Serial.print(val1);
  Serial.print(",");
  Serial.print(val2);
  Serial.print(",");
  Serial.print(thre1);
  Serial.print(",");
  Serial.print(thre2);
  Serial.println();
}


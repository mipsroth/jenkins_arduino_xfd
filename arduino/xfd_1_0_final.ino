// XFD Driver 1.0
const int XFD_SOFTWARE_VERSION = 2+4;
const int SHOW_VERSION_MS = 2000;

// teensy pins used
const int PIN_LED = 11; // internal LED on Teensy 2.0
const int PIN_CLK = 0; // serial clock (out)
const int PIN_DATA = 1; // serial data (out)
const int PIN_LATCH = 2; // latch enable (out)
const int PIN_OE = 10; // output enable (out, active low)

// test pattern change in ms
const int TEST_PATTERN_DELAY = 100;

// timeout in sec
const int TIMEOUT_SECONDS = 30;

// modes. Initial mode on startup ist TEST_PATTERN.
// After receiving valid USB commands, it is USB_LISTEN
// When no USB command is received for a long time, the mode changes to TIMEOUT
const int MODE_TEST_PATTERN = 0;
const int MODE_USB_LISTEN = 1;
const int MODE_TIMEOUT = 2;

const int ZERO = 0;
const int TIMEOUT_PATTERN_1 = 64;
const int TIMEOUT_PATTERN_2 = 128;

int mode = MODE_TEST_PATTERN;

int blinkLed = 0;
int testPatternState = 0;
int timeoutPatternState = 0;

// currently parsing input from serial
int inputColor = 0;
int inputRed = 0;
int inputYellow = 0;
int inputGreen = 0;

// last parsed serial input values for red, yellow and green.
int stateRed = 0;
int stateYellow = 0;
int stateGreen = 0;

// time of next timeout
long nextTimeout = 0;

void setup()                 
{
  pinMode(PIN_LED, OUTPUT);
  digitalWrite(PIN_LED, HIGH);

  pinMode(PIN_CLK, OUTPUT);
  pinMode(PIN_DATA, OUTPUT);
  pinMode(PIN_LATCH, OUTPUT);
  pinMode(PIN_OE, OUTPUT);
  digitalWrite(PIN_OE, LOW);
  
  // show software version
  pushSdiData(0,XFD_SOFTWARE_VERSION,0);
  delay(SHOW_VERSION_MS);
  
  Serial.begin(9600);
}


void loop()
{
  if (mode == MODE_TEST_PATTERN) {
    testPattern();
  } else if (mode == MODE_TIMEOUT) {
    timeoutPattern();
  } else if (mode == MODE_USB_LISTEN) {
    checkTimeout();
  }
  readUsbSerial();
}

void testPattern() {
  if (testPatternState<8) {
    int i = testPatternState;
    int one = 1<<i;
    pushSdiData(one, 0, 0);
  } 
  else if (testPatternState<16) {
    int i = testPatternState - 8;
    int one = 1<<i;
    pushSdiData(0, one, 0);
  } 
  else if (testPatternState<24) {
    int i = testPatternState - 16;
    int one = 1<<i;
    pushSdiData(0, 0, one);
  } 
  else if (testPatternState<32) {
    int i = testPatternState - 23;
    int bar = (1<<i) - 1;
    pushSdiData(bar, bar, bar);
  } 
  else {
    testPatternState = -1;
  }
  testPatternState++;
  delay(TEST_PATTERN_DELAY);
}

void resetTimeout() {
  nextTimeout = millis() + 1000 * TIMEOUT_SECONDS;
}

void checkTimeout() {
  if ((long)( millis() - nextTimeout ) >=0) {
    // time elapsed
    mode = MODE_TIMEOUT;
  }
}

void timeoutPattern() {
  if (timeoutPatternState == 0) {
    pushSdiData(ZERO, ZERO, TIMEOUT_PATTERN_1);
  } else if (timeoutPatternState == 10) {
    pushSdiData(ZERO, ZERO, TIMEOUT_PATTERN_2);
  } else if (timeoutPatternState >= 19) {
    timeoutPatternState = -1;
  }
  delay(TEST_PATTERN_DELAY);
  timeoutPatternState++;
}

void readUsbSerial() {
  char ch;
  if (Serial.available()) {
    ch = Serial.read();        // read a single letter
    if (ch == 'r' || ch == 'R') {
      inputColor = 'R';
    } 
    else if (ch == 'y' || ch == 'Y') {
      inputColor = 'Y';
    } 
    else if (ch == 'g' || ch == 'G') {
      inputColor = 'G';
    } 
    else if (ch >= '0' && ch <= '9') {
      parseNumber(ch - '0');
    } 
    else if (ch == ' ' || ch == '\n' || ch == ';') {
      updateDisplay();  
    }
    else if (ch == 't' || ch == 'T') {
      mode = MODE_TIMEOUT;
    }
    else if (ch == 'p' || ch == 'P') {
      mode = MODE_TEST_PATTERN;
    }
  } else {
    delay(1);
  }
}

void parseNumber(int digit) {
  if (inputColor == 'R') {
    inputRed = 10*inputRed + digit;
//    Serial.print("setting red to ");
//    Serial.println(inputRed);
  } 
  else if (inputColor == 'Y') {
    inputYellow = 10*inputYellow + digit;
//    Serial.print("setting yellow to ");
//    Serial.println(inputYellow);
  } 
  else if (inputColor == 'G') {
    inputGreen = 10*inputGreen + digit;
//    Serial.print("setting green to ");
//    Serial.println(inputGreen);
  }
}

void updateDisplay() {
//  Serial.println("updateDisplay()");

  stateGreen = inputGreen;
  stateYellow = inputYellow;
  stateRed = inputRed;
  
  pushSdiData(stateGreen, stateYellow, stateRed);
  
  mode = MODE_USB_LISTEN;
  
  inputGreen = 0;
  inputYellow = 0;
  inputRed = 0;
  inputColor = 0;
  
  resetTimeout();
}

void invertLed() {
  digitalWrite(PIN_LED, blinkLed);
  blinkLed = 255-blinkLed;
}

void pushSdiData(int dataG, int dataY, int dataR) {
  invertLed();

  //   Serial.print(", bytes: ");
  //   Serial.print(dataG);
  //   Serial.print(", ");
  //   Serial.print(dataY);
  //   Serial.print(", ");
  //   Serial.println(dataR);


  digitalWrite(PIN_LATCH, LOW);          //Pull latch LOW to start sending data
  shiftOut(PIN_DATA, PIN_CLK, MSBFIRST, dataR);         //Send the R data
  shiftOut(PIN_DATA, PIN_CLK, MSBFIRST, dataY);         //Send the Y data
  shiftOut(PIN_DATA, PIN_CLK, MSBFIRST, dataG);         //Send the G data
  digitalWrite(PIN_LATCH, HIGH);         //Pull latch HIGH to stop sending data
  digitalWrite(PIN_OE, LOW); // enable output
}



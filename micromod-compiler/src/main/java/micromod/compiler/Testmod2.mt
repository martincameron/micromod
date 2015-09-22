
(Macro test.)
Module "Testmod2"
	Channels 4
	Sequence 0
	Instrument 1 Name "Wave" Volume 64 Waveform Sawtooth Chorus 256
	Instrument 2 Name "Wave" Volume 49 Waveform Sawtooth Chorus 256
	Instrument 3 Name "Wave" Volume 36 Waveform Sawtooth Chorus 256
	Instrument 4 Name "Wave" Volume 25 Waveform Sawtooth Chorus 256
	Instrument 5 Name "Wave" Volume 16 Waveform Sawtooth Chorus 256
	Instrument 6 Name "Wave" Volume  9 Waveform Sawtooth Chorus 256
	Instrument 7 Name "Wave" Volume  4 Waveform Sawtooth Chorus 256
	Instrument 8 Name "Wave" Volume  1 Waveform Sawtooth Chorus 512
	Macro 32 Scale C-D-EF-G-A-B Root C-2 (C Major) (C Minor would be C-D#-F-G#-#-)
		Note C-2-8059
		Note ----6057
		Note ----4059
		Note ----205B
		Note ----1059
		Note ----2057
		Note ----3059
		Note ----405B
		Note ----5059
		Note ----6057
		Note ----7059
		Note ----805B
	Pattern 0
		Row "00 C-232--- -------- -------- -----F21"
		Row "03 -------- D-232--- -------- --------"
		Row "06 -------- -------- F-232--- --------"
		Row "09 -------- -------- -------- G-232---"
(End.)

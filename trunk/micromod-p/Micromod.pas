
Unit Micromod;

{ Protracker Replay In Pascal (C)2013 mumart@gmail.com }

Interface

Const MICROMOD_VERSION : String = '20130128';

Const MICROMOD_ERROR_MODULE_FORMAT_NOT_SUPPORTED : LongInt = -1;
Const MICROMOD_ERROR_SAMPLING_RATE_NOT_SUPPORTED : LongInt = -2;

{ Calculate the length in bytes of the MOD file from the 1084 byte header. }
Function MicromodCalculateFileLength( Const Header1084Bytes : Array Of ShortInt ) : LongInt;
{ Initialise the replay to play the specified module. }
Function MicromodInit( Const Module : Array Of ShortInt; SamplingRate : LongInt; Interpolation : Boolean ) : LongInt;
{ Set the sampling rate of playback.
  Returns True if the value is in range.
  Use with MicromodSetC2Rate() to adjust the playback tempo.
  For example, to play at half-speed multiply both the SamplingRate and C2Rate by 2. }
Function MicromodSetSamplingRate( SamplingRate : LongInt ) : Boolean;
{ Enable or disable the linear interpolation filter. }
Procedure MicromodSetInterpolation( Interpolation : Boolean );
{ Returns the song name. }
Function MicromodGetSongName : AnsiString;
{ Returns the specified instrument name. }
Function MicromodGetInstrumentName( InstrumentIndex : LongInt ) : AnsiString;
{ Returns the duration of the song in samples. }
Function MicromodCalculateSongDuration : LongInt;
{ Get a tick of audio.
  Returns the number of stereo sample pairs produced.
  OutputBuffer should be at least SamplingRate / 5 in length. }
Function MicromodGetAudio( Var OutputBuffer : Array Of SmallInt ) : LongInt;
{ Quickly seek to approximately SamplePos.
  Returns the actual sample position reached. }
Function MicromodSeek( SamplePos : LongInt ) : LongInt;
{ Get the current row int the pattern being played. }
Function MicromodGetRow : LongInt;
{ Get the current pattern in the sequence. }
Function MicromodGetSequencePos : LongInt;
{ Set the replay to play the specified pattern in the sequence.}
Procedure MicromodSetSequencePos( SequencePos : LongInt );
{ Get the current value of the C2Rate. }
Function MicromodGetC2Rate() : LongInt;
{ Set the value of the C2Rate.
  Returns True if the value is in range.
  This affects the frequency at which notes are played.
  The default is 8287hz for PAL/Amiga modules, or 8363hz for NTSC/PC modules. }
Function MicromodSetC2Rate( Rate : LongInt ) : Boolean;

Implementation

Uses SysUtils;

Const MAX_CHANNELS : LongInt = 32;

Const FP_SHIFT : LongInt = 15;
Const FP_ONE   : LongInt = 32768;
Const FP_MASK  : LongInt = 32767;

Const FineTuning : Array[ 0..15 ] Of Word = (
	4096, 4067, 4037, 4008, 3979, 3951, 3922, 3894,
	4340, 4308, 4277, 4247, 4216, 4186, 4156, 4126
);

Const ArpTuning : Array[ 0..15 ] Of Word = (
	4096, 4340, 4598, 4871, 5161, 5468, 5793, 6137,
	6502, 6889, 7298, 7732, 8192, 8679, 9195, 9742
);

Const SineTable : Array[ 0..31 ] Of Byte = (
	  0,  24,  49,  74,  97, 120, 141, 161, 180, 197, 212, 224, 235, 244, 250, 253,
	255, 253, 250, 244, 235, 224, 212, 197, 180, 161, 141, 120,  97,  74,  49,  24
);

Type TShortIntArray = Array Of ShortInt;

Type TNote = Record
	Key : Word;
	Instrument, Effect, Param : Byte;
End;

Type TInstrument = Record
	Name : AnsiString;
	Volume, FineTune : Byte;
	LoopStart, LoopLength : LongInt;
	SampleData : TShortIntArray;
End;

Type TChannel = Record
	Note : TNote;
	SampleIndex, SampleFrac, Step : LongInt;	
	Period, PortaPeriod : SmallInt;
	Volume, Panning, FineTune, Ampl : Byte;
	PortaSpeed, VTPhase, PLRow, FXCount, Instrument, Assigned, ID : Byte;
	VibratoSpeed, VibratoDepth, TremoloSpeed, TremoloDepth : Byte;
	TremoloAdd, VibratoAdd, ArpeggioAdd : ShortInt;
End;

Var SongName : AnsiString;
Var Channels : Array Of TChannel;
Var Instruments : Array Of TInstrument;
Var Sequence, Patterns : Array Of Byte;
Var Interpolate : Boolean;
Var RampBuffer : Array Of SmallInt;
Var NumChannels, SequenceLength, RestartPos : LongInt;
Var SampleRate, C2Rate, Gain, TickLength, RampRate : LongInt;
Var Pattern, BreakPattern, Row, NextRow, Tick : LongInt;
Var Speed, PLCount, PLChannel : LongInt;

Function CalculateNumChannels( Const Header : Array Of ShortInt ) : LongInt;
Var
	NumChan, FormatID : LongInt;
Begin
	NumChan := 0;
	If( Length( Header ) >= 1084 ) Then Begin
		FormatID := ( Header[ 1082 ] Shl 8 ) Or Header[ 1083 ];
		Case FormatID Of
			$4B2E : NumChan := 4; { M.K. }
			$4B21 : NumChan := 4; { M!K! }
			$542E : NumChan := 4; { N.T. }
			$5434 : NumChan := 4; { FLT4 }
			$484E : NumChan := Header[ 1080 ] - 48; { xCHN }
			$4348 : NumChan := ( Header[ 1080 ] - 48 ) * 10 + ( Header[ 1081 ] - 48 ); { xxCH }
		End;
	End;
	If( NumChan < 0 ) Or ( NumChan > MAX_CHANNELS ) Then NumChan := 0;
	CalculateNumChannels := NumChan;
End;

Function UBEWord( Const Data : Array Of ShortInt; Index : LongInt ) : LongInt;
Begin
	UBEWord := ( ( Data[ Index ] And $FF ) Shl 8 ) + ( Data[ Index + 1 ] And $FF );
End;

Function MicromodInit( Const Module : Array Of ShortInt; SamplingRate : LongInt; Interpolation : Boolean ) : LongInt;
Var
	NumPatterns : LongInt;
	PatIndex, SeqEntry, PatternDataLength : LongInt;
	SampleOffset, SampleLength, LoopStart, LoopLength : LongInt;
	InstIndex, Volume : LongInt;
	Instrument : TInstrument;
Begin
	NumChannels := CalculateNumChannels( Module );
	If NumChannels = 0 Then Begin
		MicromodInit := MICROMOD_ERROR_MODULE_FORMAT_NOT_SUPPORTED;
		Exit;
	End;
	SetLength( Channels, NumChannels );
	If Not MicromodSetSamplingRate( SamplingRate ) Then Begin
		MicromodInit := MICROMOD_ERROR_SAMPLING_RATE_NOT_SUPPORTED;
		Exit;
	End;
	MicromodSetInterpolation( Interpolation );
	SetLength( RampBuffer, 256 );
	If( NumChannels > 4 ) Then Begin
		C2Rate := 8363;
		Gain := 1;
	End Else Begin
		C2Rate := 8287;
		Gain := 2;
	End;
	SetLength( SongName, 20 );
	Move( Module[ 0 ], SongName[ 1 ], 20 );
	SequenceLength := Module[ 950 ] And $7F;
	RestartPos := Module[ 951 ] And $7F;
	If RestartPos >= SequenceLength Then RestartPos := 0;
	SetLength( Sequence, 128 );
	NumPatterns := 0;
	For PatIndex := 0 To 127 Do Begin
		SeqEntry := Module[ 952 + PatIndex ] And $7F;
		Sequence[ PatIndex ] := SeqEntry;
		If( SeqEntry >= NumPatterns ) Then NumPatterns := SeqEntry + 1;
	End;
	PatternDataLength := 4 * NumChannels * 64 * NumPatterns;
	SetLength( Patterns, PatternDataLength );
	Move( Module[ 1084 ], Patterns[ 0 ], PatternDataLength );
	SetLength( Instruments, 32 );
	SampleOffset := 1084 + PatternDataLength;
	For InstIndex := 1 To 31 Do Begin
		SetLength( Instrument.Name, 22 );
		Move( Module[ InstIndex * 30 - 10 ], Instrument.Name[ 1 ], 22 );
		Instrument.FineTune := Module[ InstIndex * 30 + 14 ] And $F;
		Volume := Module[ InstIndex * 30 + 15 ] And $7F;
		If Volume > 64 Then Volume := 64;		
		Instrument.Volume := Volume;
		SampleLength := UBEWord( Module, InstIndex * 30 + 12 ) * 2;
		If( SampleOffset + SampleLength > Length( Module ) ) Then
			SampleLength := Length( Module ) - SampleOffset;
		SetLength( Instrument.SampleData, SampleLength + 1 );
		Move( Module[ SampleOffset ], Instrument.SampleData[ 0 ], SampleLength );
		Instrument.SampleData[ SampleLength ] := 0;
		SampleOffset := SampleOffset + SampleLength;
		LoopStart := UBEWord( Module, InstIndex * 30 + 16 ) * 2;
		LoopLength := UBEWord( Module, InstIndex * 30 + 18 ) * 2;
		If LoopStart + LoopLength > SampleLength Then
			LoopLength := SampleLength - LoopStart;
		If LoopLength < 4 Then Begin
			LoopStart := SampleLength;
			LoopLength := 0;
		End;
		Instrument.LoopStart := LoopStart;
		Instrument.LoopLength := LoopLength;
		Instrument.SampleData[ LoopStart + LoopLength ] := Instrument.SampleData[ LoopStart ];
		Instruments[ InstIndex ] := Instrument;
	End;
	MicromodSetSequencePos( 0 );
	MicromodInit := 0;
End;

Function MicromodSetSamplingRate( SamplingRate : LongInt ) : Boolean;
Begin
	MicromodSetSamplingRate := False;
	If ( SamplingRate >= 8000 ) And ( SamplingRate <= 256000 ) Then Begin
		SampleRate := SamplingRate;
		RampRate := 256 * 2048 Div SamplingRate;
		MicromodSetSamplingRate := True;
	End;
End;

Procedure MicromodSetInterpolation( Interpolation : Boolean );
Begin
	Interpolate := Interpolation;
End;

Function MicromodGetSongName : AnsiString;
Begin
	MicromodGetSongName := '';
	If NumChannels = 0 Then Exit;
	MicromodGetSongName := SongName;
End;

Function MicromodGetInstrumentName( InstrumentIndex : LongInt ) : AnsiString;
Begin
	MicromodGetInstrumentName := '';
	If NumChannels = 0 Then Exit;
	If ( InstrumentIndex > 0 ) And ( InstrumentIndex < 32 ) Then
		MicromodGetInstrumentName := Instruments[ InstrumentIndex ].Name;
End;

Procedure SetTempo( Tempo : LongInt );
Begin
	{ Ensure TickLength is even to simplify downsampling. }
	TickLength := ( ( ( SampleRate Shl 1 ) + ( SampleRate Shr 1 ) ) Div Tempo ) And -2;
End;

Procedure Resample( Const Channel : TChannel; Var OutputBuffer : Array Of SmallInt; Length : LongInt );
Var
	OutputIndex, OutputLength : LongInt;
	Ins, Ampl, LAmpl, RAmpl : LongInt;
	SampleIdx, SampleFra, Step, LoopLen, LoopEp1 : LongInt;
	SampleData : TShortIntArray;
	C, M, Y, L, R : LongInt;
Begin
	Ins := Channel.Instrument;
	SampleData := Instruments[ Ins ].SampleData;

	Ampl := Channel.Ampl;
	If Ampl <= 0 then Exit;
	RAmpl := Ampl * Channel.Panning;
	LAmpl := Ampl * ( 255 - Channel.Panning );
	
	SampleIdx := Channel.SampleIndex;
	SampleFra := Channel.SampleFrac;
	Step := Channel.Step;
	LoopLen := Instruments[ Ins ].LoopLength;
	LoopEp1 := Instruments[ Ins ].LoopStart + LoopLen;
	
	OutputIndex := 0;
	OutputLength := Length * 2;
	
	If Interpolate Then Begin
		While OutputIndex < OutputLength Do Begin
			If SampleIdx >= LoopEp1 Then Begin
				If LoopLen <= 1 Then Break;
				While SampleIdx >= LoopEp1 Do SampleIdx := SampleIdx - LoopLen;
			End;
			
			C := SampleData[ SampleIdx ] * 256;
			M := SampleData[ SampleIdx + 1 ] * 256 - C;
			Y := ( ( M * SampleFra ) Div FP_ONE ) + C;
			L := Y * LAmpl Div 65536;
			R := Y * RAmpl Div 65536;
			
			OutputBuffer[ OutputIndex ] := OutputBuffer[ OutputIndex ] + L;
			OutputIndex := OutputIndex + 1;
			OutputBuffer[ OutputIndex ] := OutputBuffer[ OutputIndex ] + R;
			OutputIndex := OutputIndex + 1;
			
			SampleFra := SampleFra + Step;
			SampleIdx := SampleIdx + ( SampleFra Shr FP_SHIFT );
			SampleFra := SampleFra And FP_MASK;
		End;
	End Else Begin
		While OutputIndex < OutputLength Do Begin
			If SampleIdx >= LoopEp1 Then Begin
				If LoopLen <= 1 Then Break;
				While SampleIdx >= LoopEp1 Do SampleIdx := SampleIdx - LoopLen;
			End;
			
			Y := SampleData[ SampleIdx ];
			L := Y * LAmpl Div 256;
			R := Y * RAmpl Div 256;
			
			OutputBuffer[ OutputIndex ] := OutputBuffer[ OutputIndex ] + L;
			OutputIndex := OutputIndex + 1;
			OutputBuffer[ OutputIndex ] := OutputBuffer[ OutputIndex ] + R;
			OutputIndex := OutputIndex + 1;
			
			SampleFra := SampleFra + Step;
			SampleIdx := SampleIdx + ( SampleFra Shr FP_SHIFT );
			SampleFra := SampleFra And FP_MASK;
		End;
	End;
End;

Procedure UpdateSampleIndex( Var Channel : TChannel; Length : LongInt );
Var
	SampleFrac, SampleIndex, LoopStart, LoopLength, LoopOffset : LongInt;
Begin
	SampleFrac := Channel.SampleFrac + Channel.Step * Length;
	SampleIndex := Channel.SampleIndex + ( SampleFrac Shr FP_SHIFT );
	LoopStart := Instruments[ Channel.Instrument ].LoopStart;
	LoopLength := Instruments[ Channel.Instrument ].LoopLength;
	LoopOffset := SampleIndex - LoopStart;
	If loopOffset > 0 Then Begin
		SampleIndex := LoopStart;
		If LoopLength > 1 Then
			SampleIndex := SampleIndex + LoopOffset Mod LoopLength;
	End;
	Channel.SampleIndex := SampleIndex;
	Channel.SampleFrac := SampleFrac And FP_MASK;
End;

Procedure VolumeRamp( Var Buffer : Array Of SmallInt );
Var
	Offset, A1, A2 : LongInt;
Begin
	Offset := 0;
	A1 := 0;
	While A1 < 256 Do Begin
		A2 := 256 - A1;
		Buffer[ Offset ] := ( Buffer[ Offset ] * A1 + RampBuffer[ Offset ] * A2 ) Div 256;
		Offset := Offset + 1;
		Buffer[ Offset ] := ( Buffer[ Offset ] * A1 + RampBuffer[ Offset ] * A2 ) Div 256;
		Offset := Offset + 1;
		A1 := A1 + RampRate;
	End;
	Move( Buffer[ TickLength * 2 ], RampBuffer[ 0 ], 256 * SizeOf( SmallInt ) );
End;

Procedure Trigger( Var Channel : TChannel );
Var
	Period, Ins : LongInt;
Begin
	Ins := Channel.Note.Instrument;
	If Ins > 0 Then Begin
		Channel.Assigned := Ins;
		Channel.FineTune := Instruments[ Ins ].FineTune;
		Channel.Volume := Instruments[ Ins ].Volume;
		If ( Instruments[ Ins ].LoopLength > 0 ) And ( Channel.Instrument > 0 ) Then
			Channel.Instrument := Ins;
	End;
	If Channel.Note.Effect = $15 Then Channel.FineTune := Channel.Note.Param;
	If Channel.Note.Key > 0 Then Begin
		Period := ( Channel.Note.Key * FineTuning[ Channel.FineTune And $F ] ) Shr 11;
		Channel.PortaPeriod := ( Period Shr 1 ) + ( Period And 1 );
		If ( Channel.Note.Effect <> $3 ) And ( Channel.Note.Effect <> $5 ) Then Begin
			Channel.Instrument := Channel.Assigned;
			Channel.Period := Channel.PortaPeriod;
			Channel.SampleIndex := 0;
			Channel.SampleFrac := 0;
			Channel.VTPhase := 0;
		End;
	End;
End;

Procedure Vibrato( Var Channel : TChannel );
Var
	Phase, Out : LongInt;
Begin
	Phase := Channel.VTPhase * Channel.VibratoSpeed;
	Out := ( SineTable[ Phase And $1F ] * Channel.VibratoDepth ) Shr 7;
	If ( Phase And $20 ) > 0 Then Out := -Out;
	Channel.VibratoAdd := Out;
End;

Procedure Tremolo( Var Channel : TChannel );
Var
	Phase, Out : LongInt;
Begin
	Phase := Channel.VTPhase * Channel.TremoloSpeed;
	Out := ( SineTable[ Phase And $1F ] * Channel.TremoloDepth ) Shr 6;
	If ( Phase And $20 ) > 0 Then Out := -Out;
	Channel.TremoloAdd := Out;
End;

Procedure VolumeSlide( Var Channel : TChannel; Param : LongInt );
Var
	Volume : LongInt;
Begin
	Volume := Channel.Volume + ( Param Shr 4 ) - ( Param And $F );
	If Volume > 64 Then Volume := 64;
	If Volume < 0 Then Volume := 0;
	Channel.Volume := Volume;
End;

Procedure UpdateFrequency( Var Channel : TChannel );
Var
	Period, Freq, Volume : LongInt;
Begin
	Period := Channel.Period + Channel.VibratoAdd;
	If Period < 14 Then Period := 14;
	Freq := C2Rate * 107 Div Period;
	Freq := ( ( Freq * ArpTuning[ Channel.ArpeggioAdd ] ) Shr 12 ) And $FFFF;
	Channel.Step := ( Freq Shl FP_SHIFT ) Div ( SampleRate Shr 2 );
	Volume := Channel.Volume + Channel.TremoloAdd;
	If Volume > 64 Then Volume := 64;
	If Volume < 0 Then Volume := 0;
	Channel.Ampl := Volume * Gain;
End;

Procedure ChannelRow( Var Channel : TChannel );
Var
	Effect, Param, Volume, Period : LongInt;
Begin	
	Effect := Channel.Note.Effect;
	Param := Channel.Note.Param;
	If Effect <> $1D Then Trigger( Channel );
	Channel.ArpeggioAdd := 0;
	Channel.VibratoAdd := 0;
	Channel.TremoloAdd := 0;
	Channel.FXCount := 0;
	Case Effect Of
		$3 : Begin { Tone Portamento }
				If Param > 0 Then Channel.PortaSpeed := Param;
			End;
		$4 : Begin { Vibrato. }
				If ( Param Shr  4 ) > 0 Then Channel.VibratoSpeed := Param Shr 4;
				If ( Param And $F ) > 0 Then Channel.VibratoDepth := Param And $F;
				Vibrato( Channel );
			End;
		$6 : Begin { Vibrato + Volume Slide. }
				Vibrato( Channel );
			End;
		$7 : Begin { Tremolo }
				If ( Param Shr  4 ) > 0 Then Channel.TremoloSpeed := Param Shr 4;
				If ( Param And $F ) > 0 Then Channel.TremoloDepth := Param And $F;
				Tremolo( Channel );
			End;
		$8 : Begin { Set Panning }
				If NumChannels > 4 Then Channel.Panning := Param;
			End;
		$9 : Begin { Set Sample Position }
				Channel.SampleIndex := Param Shl 8;
				Channel.SampleFrac := 0;
			End;
		$B : Begin { Pattern Jump. }
				If PLCount < 0 Then Begin
					BreakPattern := Param;
					NextRow := 0;
				End;
			End;
		$C : Begin { Set Volume }
				If Param > 64 Then
					Channel.Volume := 64
				Else
					Channel.Volume := Param;
			End;
		$D : Begin { Pattern Break. }
				If PLCount < 0 Then Begin
					BreakPattern := Pattern + 1;
					NextRow := ( Param Shr 4 ) * 10 + ( Param And $F );
					If NextRow >= 64 Then NextRow := 0;
				End;
			End;
		$F : Begin { Set Speed }
				If Param > 0 Then
					If Param < 32 Then Begin
						Speed := Param;
						Tick := Speed;
					End Else Begin
						SetTempo( Param );
					End;
			End;
		$11 : Begin { Fine Portamento Up }
				Period := Channel.Period - Param;
				If Period < 0 Then Period := 0;
				Channel.Period := Period;
			End;
		$12 : Begin { Fine Portamento Down }
				Period := Channel.Period + Param;
				If Period > 65535 Then Period := 65535;
				Channel.Period := Period;
			End;
		$16 : Begin { Pattern Loop }
				If Param = 0 Then Channel.PLRow := Row;
				If Channel.PLRow < Row Then Begin
					If PlCount < 0 Then Begin
						PLCount := Param;
						PLChannel := Channel.ID;
					End;
					If PLChannel = Channel.ID Then Begin
						If PLCount = 0 Then Begin
							Channel.PLRow := Row + 1;
						End Else Begin
							NextRow := Channel.PLRow;
							BreakPattern := -1;
						End;
						PLCount := PLCount - 1;
					End;
				End;
			End;
		$1A : Begin { Fine Volume Up }
				Volume := Channel.Volume + Param;
				If Volume > 64 Then Volume := 64;
				Channel.Volume := Volume;
			End;
		$1B : Begin { Fine Volume Down }
				Volume := Channel.Volume - Param;
				If Volume < 0 Then Volume := 0;
				Channel.Volume := Volume;
			End;
		$1C : Begin { Note Cut }
				If Param <= 0 Then Channel.Volume := 0;
			End;
		$1D : Begin { Note Delay }
				If Param <= 0 Then Trigger( Channel );
			End;
		$1E : Begin { Pattern Delay }
				Tick := Speed + Speed * Param;
			End;
	End;
	UpdateFrequency( Channel );
End;

Function SequenceRow : Boolean;
Var
	SongEnd : Boolean;
	PatternOffset, Chan : LongInt;
	Effect, Param : Byte;
	Note : TNote;
Begin
	SongEnd := False;
	If BreakPattern >= 0 Then Begin
		If BreakPattern >= SequenceLength Then Begin
			BreakPattern := 0;
			NextRow := 0;
		End;
		If BreakPattern <= Pattern Then SongEnd := True;
		Pattern := BreakPattern;
		For Chan := 0 To NumChannels - 1 Do Channels[ Chan ].PLRow := 0;
		BreakPattern := -1;
	End;
	Row := NextRow;
	NextRow := Row + 1;
	If NextRow >= 64 Then Begin
		BreakPattern := Pattern + 1;
		NextRow := 0;
	End;
	PatternOffset := ( Sequence[ Pattern ] * 64 + Row ) * NumChannels * 4;
	For Chan := 0 To NumChannels - 1 Do Begin
		Note.Key := ( Patterns[ PatternOffset ] And $F ) Shl 8;
		Note.Key := Note.Key Or Patterns[ PatternOffset + 1 ];
		Note.Instrument := ( Patterns[ PatternOffset + 2 ] And $F0 ) Shr 4;
		Note.Instrument := Note.Instrument Or ( Patterns[ PatternOffset ] And $10 );
		Effect := Patterns[ PatternOffset + 2 ] And $F;
		Param := Patterns[ PatternOffset + 3 ];
		PatternOffset := PatternOffset + 4;
		If Effect = $E Then Begin
			Effect := $10 Or ( Param Shr 4 );
			Param := Param And $F;
		End;
		If( Effect = 0 ) And ( Param > 0 ) Then Effect := $E;
		Note.Effect := Effect;
		Note.Param := Param;
		Channels[ Chan ].Note := Note;
		ChannelRow( Channels[ Chan ] );
	End;
	SequenceRow := SongEnd;
End;

Procedure TonePortamento( Var Channel : TChannel );
Var
	Source, Destin : LongInt;
Begin
	Source := Channel.Period;
	Destin := Channel.PortaPeriod;
	If Source < Destin Then Begin
		Source := Source + Channel.PortaSpeed;
		If( Source > Destin ) Then Source := Destin;
	End;
	If Source > Destin Then Begin
		Source := Source - Channel.PortaSpeed;
		If( Source < Destin ) Then Source := Destin;
	End;
	Channel.Period := Source;
End;

Procedure ChannelTick( Var Channel : TChannel );
Var
	Effect, Param, Period : LongInt;
Begin
	Effect := Channel.Note.Effect;
	Param := Channel.Note.Param;
	Channel.VTPhase := Channel.VTPhase + 1;
	Channel.FXCount := Channel.FXCount + 1;
	Case Effect Of
		$1 : Begin { Portamento Up.}
				Period := Channel.Period - Param;
				If Period < 0 Then Period := 0;
				Channel.Period := Period;
			End;
		$2 : Begin { Portamento Down. }
				Period := Channel.Period + Param;
				If Period > 65535 Then Period := 65535;
				Channel.Period := Period;
			End;
		$3 : Begin { Tone Portamento. }
				TonePortamento( Channel );
			End;
		$4 : Begin { Vibrato. }
				Vibrato( Channel );
			End;
		$5 : Begin { Tone Portamento + Volume Slide. }
				TonePortamento( Channel );
				VolumeSlide( Channel, Param );
			End;
		$6 : Begin { Vibrato + Volume Slide. }
				Vibrato( Channel );
				VolumeSlide( Channel, Param );
			End;
		$7 : Begin { Tremolo. }
				Tremolo( Channel );
			End;
		$A : Begin { Volume Slide }
				VolumeSlide( Channel, Param );
			End;
		$E : Begin { Arpeggio }
				If Channel.FXCount > 2 Then Channel.FXCount := 0;
				If Channel.FXCount = 0 Then Channel.ArpeggioAdd := 0;
				If Channel.FXCount = 1 Then Channel.ArpeggioAdd := Param Shr 4;
				If Channel.FXCount = 2 Then Channel.ArpeggioAdd := Param And $F;
			End;
		$19 : Begin { Retrig }
				If Channel.FXCount >= Param Then Begin
					Channel.FXCount := 0;
					Channel.SampleIndex := 0;
					Channel.SampleFrac := 0;
				End;
			End;
		$1C : Begin { Note Cut }
				If Param = Channel.FXCount Then Channel.Volume := 0;
			End;
		$1D : Begin { Note Delay }
				If Param = Channel.FXCount Then Trigger( Channel );
			End;
	End;
	If Effect > 0 Then UpdateFrequency( Channel );
End;

Function SequenceTick : Boolean;
Var
	SongEnd : Boolean;
	Chan : LongInt;
Begin
	SongEnd := False;
	Tick := Tick - 1;
	If Tick <= 0 Then Begin
		Tick := Speed;
		SongEnd := SequenceRow;
	End Else Begin
		For Chan := 0 To NumChannels - 1 Do ChannelTick( Channels[ Chan ] );
	End;
	SequenceTick := SongEnd;
End;

Function MicromodGetAudio( Var OutputBuffer : Array Of SmallInt ) : LongInt;
Var
	Chan : LongInt;
Begin
	MicromodGetAudio := 0;
	If NumChannels > 0 Then Begin;
		FillChar( OutputBuffer[ 0 ], ( TickLength + 128 ) * 2 * SizeOf( SmallInt ), 0 );
		For Chan := 0 To NumChannels - 1 Do Begin
			Resample( Channels[ Chan ], OutputBuffer, TickLength + 128 );
			UpdateSampleIndex( Channels[ Chan ], TickLength );
		End;
		VolumeRamp( OutputBuffer );
		SequenceTick();
		MicromodGetAudio := TickLength;
	End;
End;

Function MicromodSeek( SamplePos : LongInt ) : LongInt;
Var
	CurrentPos, Chan : LongInt;
Begin
	CurrentPos := 0;
	If NumChannels > 0 Then Begin;
		MicromodSetSequencePos( 0 );
		While ( SamplePos - CurrentPos ) >= TickLength Do Begin
			For Chan := 0 To NumChannels - 1 Do
				UpdateSampleIndex( Channels[ Chan ], TickLength );
			CurrentPos := CurrentPos + TickLength;
			SequenceTick();
		End;
	End;
	MicromodSeek := CurrentPos;
End;

Function MicromodCalculateSongDuration : LongInt;
Var
	SongEnd : Boolean;
	Duration : LongInt;
Begin
	Duration := 0;
	If NumChannels > 0 Then Begin;
		MicromodSetSequencePos( 0 );
		SongEnd := False;
		While Not SongEnd Do Begin
			Duration := Duration + TickLength;
			SongEnd := SequenceTick();
		End;
		MicromodSetSequencePos( 0 );
	End;
	MicromodCalculateSongDuration := Duration;
End;

Function MicromodGetRow : LongInt;
Begin
	MicromodGetRow := Row;
End;

Function MicromodGetSequencePos : LongInt;
Begin
	MicromodGetSequencePos := Pattern;
End;

Procedure MicromodSetSequencePos( SequencePos : LongInt );
Var
	Chan : LongInt;
Begin
	If NumChannels = 0 Then Exit;
	If SequencePos >= SequenceLength Then SequencePos := 0;
	BreakPattern := SequencePos;
	NextRow := 0;
	Tick := 1;
	Speed := 6;
	SetTempo( 125 );
	PLCount := -1;
	PLChannel := -1;
	For Chan := 0 To NumChannels - 1 Do Begin
		FillChar( Channels[ Chan ], SizeOf( TChannel ), 0 );
		Channels[ Chan ].ID := Chan;
		Case Chan And 3 Of
			0 : Channels[ Chan ].Panning := 51;
			1 : Channels[ Chan ].Panning := 204;
			2 : Channels[ Chan ].Panning := 204;
			3 : Channels[ Chan ].Panning := 51;
		End;
	End;
	FillChar( RampBuffer[ 0 ], 256 * SizeOf( SmallInt ), 0 );
	SequenceTick();
End;

Function MicromodGetC2Rate() : LongInt;
Begin
	MicromodGetC2Rate := C2Rate;
End;

Function MicromodSetC2Rate( Rate : LongInt ) : Boolean;
Begin
	MicromodSetC2Rate := False;
	If ( Rate > 0 ) And ( Rate < 65536 ) Then Begin
		C2Rate := Rate;
		MicromodSetC2Rate := True;
	End;
End;

Function MicromodCalculateFileLength( Const Header1084Bytes : Array Of ShortInt ) : LongInt;
Var
	NumChan, NumPatterns, PatIndex, SeqEntry, Length, InstIndex : LongInt;
Begin
	Length := MICROMOD_ERROR_MODULE_FORMAT_NOT_SUPPORTED;
	NumChan := CalculateNumChannels( Header1084Bytes );
	If NumChan > 0 Then Begin
		NumPatterns := 0;
		For PatIndex := 0 To 127 Do Begin
			SeqEntry := Header1084Bytes[ 952 + PatIndex ] And $7F;
			If( SeqEntry >= NumPatterns ) Then NumPatterns := SeqEntry + 1;
		End;
		Length := 1084 + 4 * NumChan * 64 * NumPatterns;
		For InstIndex := 1 To 31 Do Begin
			Length := Length + UBEWord( Header1084Bytes, InstIndex * 30 + 12 ) * 2;
		End;
	End;
	MicromodCalculateFileLength := Length;
End;

End.

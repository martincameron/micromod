
Program W32ModPlayer;

{$APPTYPE Console}

Uses SysUtils, MMSystem, Windows, Micromod;

{
	Simple command-line test player for Micromod using the Windows MMAPI.
}

Const SAMPLING_FREQ  : LongInt = 48000; { 48khz. }
Const NUM_CHANNELS   : LongInt = 2;     { Stereo. }
Const BUFFER_SAMPLES : LongInt = 16384; { 64k per buffer. }
Const NUM_BUFFERS    : LongInt = 8;     { 8 buffers. }

Const EXIT_FAILURE   : Integer = 1;

Var Semaphore : THandle;
Var MixBuffer : Array Of SmallInt;
Var MixIndex, MixLength : LongInt;

Procedure WaveOutProc( hWaveOut: HWAVEOUT; uMsg: UINT; dwInstance, dwParam1, dwParam2: DWORD_PTR ) StdCall;
Begin
	//if uMsg = WOM_OPEN then Writeln( 'Device open.' );
	If uMsg = WOM_DONE Then
	Begin
		ReleaseSemaphore( Semaphore, 1, Nil );
	End;
	//if uMsg = WOM_CLOSE then Writeln( 'Device closed.' );
End;

Procedure CheckMMError( ReturnCode : MMRESULT );
Var
	ErrorText : Array[ 0..63 ] Of Char;
Begin
	If ReturnCode <> MMSYSERR_NOERROR Then Begin
		WaveOutGetErrorText( ReturnCode, @ErrorText, Length( ErrorText ) );
		WriteLn( String( ErrorText ) );
		Halt( EXIT_FAILURE );
	End;
End;

Procedure LoadModule( FileName : String );
	Var ModuleFile : File;
	Var ModuleData : Array Of ShortInt;
	Var FileLength, ReadLength, ReturnCode: LongInt;
Begin
	If Not FileExists( FileName ) Then Begin
		WriteLn( 'File Not Found: ' + FileName );
		Halt( EXIT_FAILURE );
	End;
	FileMode := fmOpenRead;
	Assign( ModuleFile, FileName );
	Reset( ModuleFile, 1 );
	SetLength( ModuleData, 1084 );
	BlockRead( ModuleFile, ModuleData[ 0 ], 1084, ReadLength );
	If ReadLength < 1084 Then Begin
		WriteLn( 'Unable to read module header!' );
		Halt( EXIT_FAILURE );
	End;
	FileLength := MicromodCalculateFileLength( ModuleData );
	If FileLength = MICROMOD_ERROR_MODULE_FORMAT_NOT_SUPPORTED Then Begin
		WriteLn( 'Module format not supported!' );
		Halt( EXIT_FAILURE );
	End;
	SetLength( ModuleData, FileLength );
	BlockRead( ModuleFile, ModuleData[ 1084 ], FileLength - 1084, ReadLength );
	Close( ModuleFile );
	If ReadLength + 1084 < FileLength Then
		WriteLn( 'Module File Has Been Truncated! Should Be ' + IntToStr( FileLength ) );
	ReturnCode := MicromodInit( ModuleData, SAMPLING_FREQ, False );
	If ReturnCode <> 0 Then Begin
		WriteLn( 'Unable to initialize replay! ' + IntToStr( ReturnCode ) );
		Halt( EXIT_FAILURE );
	End;
End;

Procedure PrintModuleInfo;
Var
	Idx : LongInt;
	InstrumentName : String;
Begin
	WriteLn( 'Song Name: ' + MicromodGetSongName );
	For Idx := 1 To 31 Do Begin
		InstrumentName := TrimRight( MicromodGetInstrumentName( Idx ) );
		If Length( InstrumentName ) > 0 Then Begin
			Write( 'Instrument ' );
			If Idx < 10 Then Write( ' ' );
			WriteLn( IntToStr( Idx ) + ': ' + InstrumentName );
		End;
	End;
End;

Procedure GetAudio( Var OutputBuffer : Array Of SmallInt; Length : LongInt );
Var
	OutOffset, OutRemain, Count : LongInt;
Begin
	OutOffset := 0;
	While OutOffset < Length Do Begin
		OutRemain := Length - OutOffset;
		Count := MixLength - MixIndex;
		If Count > OutRemain Then Count := OutRemain;
		Move( MixBuffer[ MixIndex * 2 ], OutputBuffer[ OutOffset * 2 ], Count * 2 * SizeOf( SmallInt ) );
		MixIndex := MixIndex + Count;
		If MixIndex >= MixLength Then Begin
			MixLength := MicromodGetAudio( MixBuffer );
			MixIndex := 0;
		End;
		OutOffset := OutOffset + Count;
	End;
End;

Procedure PlayModule;
Var
	WaveFormat : TWaveFormatEx;
	WaveOutHandle : HWaveOut;
	WaveHeaders : Array Of TWaveHdr;
	WaveBuffers : Array Of Array Of SmallInt;
	PWaveHeader : PWaveHdr;
	Idx, Err, CurrentBuffer, SamplesRemaining, Count : LongInt;
Begin
	{ Initialise Wave Format Structure. }
	WaveFormat.wFormatTag := WAVE_FORMAT_PCM;
	WaveFormat.nChannels := NUM_CHANNELS;
	WaveFormat.nSamplesPerSec := SAMPLING_FREQ;
	WaveFormat.nAvgBytesPerSec := SAMPLING_FREQ * NUM_CHANNELS * 2;
	WaveFormat.nBlockAlign := NUM_CHANNELS * 2;
	WaveFormat.wBitsPerSample := 16;
	
	{ Initialise Waveform Buffers. }
	SetLength( WaveBuffers, NUM_BUFFERS, BUFFER_SAMPLES * NUM_CHANNELS );
	SetLength( WaveHeaders, NUM_BUFFERS );
	For Idx := 0 To NUM_BUFFERS - 1 Do Begin
		FillChar( WaveHeaders[ Idx ], SizeOf( TWaveHdr ), 0 );
		WaveHeaders[ Idx ].lpData := @WaveBuffers[ Idx ][ 0 ];
		WaveHeaders[ Idx ].dwBufferLength := BUFFER_SAMPLES * NUM_CHANNELS * 2;
	End;
	
	{ Initialise Semaphore. }
	Semaphore := CreateSemaphore( Nil, NUM_BUFFERS, NUM_BUFFERS, '' );

	{ Open Audio Device. }
	Err := WaveOutOpen( @WaveOutHandle, WAVE_MAPPER, @WaveFormat, DWORD_PTR( @WaveOutProc ), 0, CALLBACK_FUNCTION );
	CheckMMError( Err );

	{ Calculate Duration. }
	SamplesRemaining := MicromodCalculateSongDuration;
	WriteLn( 'Duration: ' + IntToStr( SamplesRemaining Div SAMPLING_FREQ ) + ' Seconds.' );

	{ Initialise Mix Buffer. }
	SetLength( MixBuffer, SAMPLING_FREQ * 2 Div 5 );

	{ Play Through Once. }
	CurrentBuffer := 0;
	While SamplesRemaining > 0 Do Begin
		{ Wait for a buffer to become available. }
		WaitForSingleObject( Semaphore, INFINITE );
		
		{ Get audio from replay. }
		Count := BUFFER_SAMPLES;
		If Count > SamplesRemaining Then Begin
			{ Last buffer, clear as it will be partially filled. }
			FillChar( WaveBuffers[ CurrentBuffer ][ 0 ], BUFFER_SAMPLES * NUM_CHANNELS * 2, 0 );
			Count := SamplesRemaining;
		End;
		GetAudio( WaveBuffers[ CurrentBuffer ], Count );
		SamplesRemaining := SamplesRemaining - Count;
		
		{ Submit buffer to audio system. }
		PWaveHeader := @WaveHeaders[ CurrentBuffer ];
		CheckMMError( WaveOutUnprepareHeader( WaveOutHandle, PWaveHeader, SizeOf( TWaveHdr ) ) );
		CheckMMError( WaveOutPrepareHeader( WaveOutHandle, PWaveHeader, SizeOf( TWaveHdr ) ) );
		CheckMMError( WaveOutWrite( WaveOutHandle, PWaveHeader, SizeOf( TWaveHdr ) ) );
		
		{ Next buffer. }
		CurrentBuffer := CurrentBuffer + 1;
		If CurrentBuffer >= NUM_BUFFERS Then CurrentBuffer := 0;
	End;

	{ Close audio device when finished. }
	While WaveOutClose( WaveOutHandle ) = WAVERR_STILLPLAYING Do Sleep( 100 );
End;

Begin
	{ Initialize Replay. }
	If ParamCount > 0 Then Begin
		LoadModule( ParamStr( 1 ) );
		PrintModuleInfo;
		PlayModule;	
	End Else Begin
		WriteLn( 'Micromod ProTracker replay in Pascal.' );
		WriteLn( 'Please specify a module file to play.' );
	End;
End.


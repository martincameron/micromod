
Program SDLModPlayer;

{$APPTYPE Console}

Uses SysUtils, SDL, Micromod;

{
	Simple command-line test player for Micromod using SDL.
}

Const SAMPLING_FREQ  : LongInt = 48000; { 48khz. }
Const NUM_CHANNELS   : LongInt = 2;     { Stereo. }
Const BUFFER_SAMPLES : LongInt = 65536; { 256k buffer. }

Const EXIT_FAILURE   : Integer = 1;

Var Semaphore : PSDL_Sem;
Var MixBuffer : Array Of SmallInt;
Var SamplesRemaining, MixIndex, MixLength : LongInt;

Procedure LoadModule( FileName : AnsiString );
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
		InstrumentName := MicromodGetInstrumentName( Idx );
		If InstrumentName[ 1 ] <> #0 Then Begin
			Write( 'Instrument ' );
			If Idx < 10 Then Write( ' ' );
			WriteLn( IntToStr( Idx ) + ': ' + InstrumentName );
		End;
	End;
End;

Procedure GetAudio( UserData : Pointer; OutputBuffer : PUInt8; Length : LongInt ); CDecl;
Var
	OutOffset, OutRemain, Count : LongInt;
Begin
	OutOffset := 0;
	Length := Length Div 4;
	While OutOffset < Length Do Begin
		OutRemain := Length - OutOffset;
		Count := MixLength - MixIndex;
		If Count > OutRemain Then Count := OutRemain;
		Move( MixBuffer[ MixIndex * 2 ], OutputBuffer[ OutOffset * 4 ], Count * 2 * SizeOf( SmallInt ) );
		MixIndex := MixIndex + Count;
		If MixIndex >= MixLength Then Begin
			{ Get more audio from replay. }
			MixLength := MicromodGetAudio( MixBuffer );
			MixIndex := 0;
			{ Notify main thread if song has finished. }
			SamplesRemaining := SamplesRemaining - MixLength;
			If SamplesRemaining <= 0 Then SDL_SemPost( Semaphore );
		End;
		OutOffset := OutOffset + Count;
	End;
End;

Procedure PlayModule;
Var
	AudioSpec : TSDL_AudioSpec;
Begin
	{ Calculate Duration. }
	SamplesRemaining := MicromodCalculateSongDuration;
	WriteLn( 'Duration: ' + IntToStr( SamplesRemaining Div SAMPLING_FREQ ) + ' Seconds.' );

	{ Initialise Mix Buffer. }
	SetLength( MixBuffer, SAMPLING_FREQ * 2 Div 5 );

	{ Open Audio Device. }
	FillChar( AudioSpec, SizeOf( TSDL_AudioSpec ), 0 );
	AudioSpec.freq := SAMPLING_FREQ;
	AudioSpec.format := AUDIO_S16SYS;
	AudioSpec.channels := NUM_CHANNELS;
	AudioSpec.samples := BUFFER_SAMPLES;
	AudioSpec.callback := @GetAudio;
	AudioSpec.userdata := Nil;
	if SDL_OpenAudio( @AudioSpec, Nil ) <> 0 Then Begin
		WriteLn( 'Couldn''t open audio device: ' + SDL_GetError() );
		Halt( EXIT_FAILURE );
	End;

	{ Begin playback. }
	SDL_PauseAudio( 0 );

	{ Wait for playback to finish. }
	Semaphore := SDL_CreateSemaphore( 0 );
	if SDL_SemWait( Semaphore ) <> 0 Then WriteLn( 'SDL_SemWait() failed.' );

	{ Close audio device. }
	SDL_CloseAudio();
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


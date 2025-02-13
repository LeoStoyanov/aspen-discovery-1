<?php

require_once ROOT_DIR . '/sys/DB/DataObject.php';

class CoverImage extends DataObject
{
	public $__table = 'cover_image';
	private int $id;
	private int $recordId;
	private string $recordType;
	private ?string $imageSource;
	private ?string $imageUrl;
	private string $lastProcessed;
	private bool $shouldReloadCover;

	public function getNumericColumnNames(): array {
		return ['id', 'shouldReloadCover'];
	}

	/**
	 * @return bool
	 */
	public function getShouldReloadCover(): bool
	{
		return $this->shouldReloadCover;
	}

	/**
	 * @param bool $shouldReloadCover
	 */
	public function setShouldReloadCover(bool $shouldReloadCover): void
	{
		$this->shouldReloadCover = $shouldReloadCover;
	}

	/**
	 * @return string
	 */
	public function getLastProcessed(): string
	{
		return $this->lastProcessed;
	}

	/**
	 * @param string $lastProcessed
	 */
	public function setLastProcessed(string $lastProcessed): void
	{
		$this->lastProcessed = $lastProcessed;
	}

	/**
	 * @return string
	 */
	public function getType(): string
	{
		return $this->type;
	}

	/**
	 * @param string $type
	 */
	public function setType(string $type): void
	{
		$this->type = $type;
	}

	/**
	 * @return ?string
	 */
	public function getImageUrl(): ?string
	{
		return $this->imageUrl;
	}

	/**
	 * @param ?string $imageUrl
	 */
	public function setImageUrl(?string $imageUrl): void
	{
		$this->imageUrl = $imageUrl;
	}

	/**
	 * @return ?string
	 */
	public function getImageSource(): ?string
	{
		return $this->imageSource;
	}

	/**
	 * @param ?string $imageSource
	 */
	public function setImageSource(?string $imageSource): void
	{
		$this->imageSource = $imageSource;
	}

	/**
	 * @return int
	 */
	public function getId(): int
	{
		return $this->id;
	}

	/**
	 * @param int $id
	 */
	public function setId(int $id): void
	{
		$this->id = $id;
	}

	/**
	 * @return int
	 */
	public function getRecordId(): int
	{
		return $this->recordId;
	}

	/**
	 * @param int $recordId
	 */
	public function setRecordId(int $recordId): void
	{
		$this->recordId = $recordId;
	}

	/**
	 * @return string
	 */
	public function getRecordType(): string
	{
		return $this->recordType;
	}

	/**
	 * @param string $recordType
	 */
	public function setRecordType(string $recordType): void
	{
		$this->recordType = $recordType;
	}


}